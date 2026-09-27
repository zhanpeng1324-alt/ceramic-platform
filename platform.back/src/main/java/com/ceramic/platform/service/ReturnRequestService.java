package com.ceramic.platform.service;

import com.ceramic.platform.entity.Order;
import com.ceramic.platform.entity.ReturnRequest;
import com.ceramic.platform.entity.ReturnRequestLog;
import com.ceramic.platform.mapper.ReturnRequestLogMapper;
import com.ceramic.platform.mapper.ReturnRequestMapper;
import com.ceramic.platform.mapper.SeckillOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 售后服务。
 *
 * 统一状态机：
 * PENDING → APPROVED / REJECTED / CLOSED
 * APPROVED → RETURNING / REFUNDING / CLOSED
 * RETURNING → RECEIVED / CLOSED
 * RECEIVED → REFUNDING / CLOSED
 * REFUNDING → REFUNDED / CLOSED
 * 终态：REFUNDED、REJECTED、CLOSED
 */
@Service
public class ReturnRequestService {

    /** 合法状态流转映射（from → 允许到达的 to 集合）。 */
    private static final Map<String, Set<String>> LEGAL_TRANSITIONS = Map.of(
            "PENDING", Set.of("APPROVED", "REJECTED", "CLOSED"),
            "APPROVED", Set.of("RETURNING", "REFUNDING", "CLOSED"),
            "RETURNING", Set.of("RECEIVED", "CLOSED"),
            "RECEIVED", Set.of("REFUNDING", "CLOSED"),
            "REFUNDING", Set.of("REFUNDED", "CLOSED"),
            "REFUNDED", Set.of(),
            "REJECTED", Set.of(),
            "CLOSED", Set.of()
    );

    private static final Set<String> TERMINAL_STATUSES = Set.of("REFUNDED", "REJECTED", "CLOSED");

    /** 允许申请售后的订单状态（已支付/已发货/已完成）。 */
    private static final Set<String> ALLOWED_ORDER_STATUSES = Set.of("PAID", "SHIPPED", "COMPLETED");

    private final ReturnRequestMapper returnRequestMapper;
    private final ReturnRequestLogMapper logMapper;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final SeckillOrderMapper seckillOrderMapper;

    public ReturnRequestService(ReturnRequestMapper returnRequestMapper,
                                ReturnRequestLogMapper logMapper,
                                OrderService orderService,
                                PaymentService paymentService,
                                NotificationService notificationService,
                                SeckillOrderMapper seckillOrderMapper) {
        this.returnRequestMapper = returnRequestMapper;
        this.logMapper = logMapper;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.seckillOrderMapper = seckillOrderMapper;
    }

    public List<ReturnRequest> list(Integer userId) {
        return userId == null ? returnRequestMapper.findAll() : returnRequestMapper.findByUserId(userId);
    }

    public List<ReturnRequest> findByOrderId(Integer orderId) {
        return returnRequestMapper.findByOrderId(orderId);
    }

    public ReturnRequest findById(Integer id) {
        return returnRequestMapper.findById(id);
    }

    public List<ReturnRequestLog> getLogs(Long returnRequestId) {
        return logMapper.findByReturnRequestId(returnRequestId);
    }

    /** 客户创建售后申请：校验订单归属与状态。 */
    @Transactional
    public ReturnRequest create(ReturnRequest req, Long currentUserId, String role) {
        if (req.getOrderId() == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        if (req.getProductId() == null) {
            throw new IllegalArgumentException("请选择售后商品");
        }
        if (req.getReturnType() == null || req.getReturnType().isBlank()) {
            throw new IllegalArgumentException("请选择售后类型");
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new IllegalArgumentException("请填写售后原因");
        }

        Order order = orderService.findById(req.getOrderId().longValue());
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if ("customer".equals(role) && !order.getUserId().equals(currentUserId)) {
            throw new IllegalArgumentException("无权为他人订单申请售后");
        }
        String orderStatus = order.getStatus() == null ? "" : order.getStatus().toUpperCase();
        if (!ALLOWED_ORDER_STATUSES.contains(orderStatus)) {
            throw new IllegalArgumentException("当前订单状态不允许申请售后: " + order.getStatus());
        }

        req.setUserId(currentUserId.intValue());
        req.setStatus("PENDING");
        if (req.getRefundAmount() == null && req.getUnitPrice() != null && req.getQuantity() != null) {
            req.setRefundAmount(req.getUnitPrice().multiply(BigDecimal.valueOf(req.getQuantity())));
        }
        if (req.getRefundAmount() == null) {
            req.setRefundAmount(BigDecimal.ZERO);
        }
        returnRequestMapper.insert(req);
        recordLog(req.getId().longValue(), null, "PENDING", currentUserId, role, "客户提交售后申请");

        // 未发货秒退：已支付未发货（PAID）的订单免人工审核，系统自动原路退款。
        // 无需退货（用户未收到货），复用现有状态机：PENDING → APPROVED → REFUNDING → REFUNDED
        if ("PAID".equals(orderStatus)) {
            returnRequestMapper.updateStatus(req.getId(), "APPROVED");
            recordLog(req.getId().longValue(), "PENDING", "APPROVED", null, "system", "订单未发货，退款免审核自动通过");
            returnRequestMapper.updateStatus(req.getId(), "REFUNDING");
            BigDecimal amt = req.getRefundAmount();
            if (amt == null || amt.signum() <= 0) {
                amt = order.getTotalAmount();
            }
            // 封顶：不得超过订单实付金额
            if (order.getTotalAmount() != null && amt.compareTo(order.getTotalAmount()) > 0) {
                amt = order.getTotalAmount();
            }
            returnRequestMapper.updateRefund(req.getId(), "REFUNDED", amt);
            paymentService.refundByOrder(req.getOrderId().longValue(), amt);
            // 秒杀退款口径（详见 syncSeckillRefunded）：不回补库存与资格，仅同步秒杀单状态
            syncSeckillRefunded(order);
            recordLog(req.getId().longValue(), "REFUNDING", "REFUNDED", null, "system", "未发货自动退款 ¥" + amt);
            if (req.getUserId() != null) {
                notificationService.create(req.getUserId().longValue(), "RETURN", "退款已完成",
                        "您的订单尚未发货，退款 ¥" + amt + " 已自动原路退回，请注意查收。", "RETURN", req.getId().longValue());
            }
        }
        return req;
    }

    /** 管理员/客服审核通过：PENDING → APPROVED，可填写退货地址。 */
    @Transactional
    public void approve(Integer id, String returnAddress, Long operatorId, String role) {
        assertStaff(role);
        ReturnRequest req = requireExists(id);
        assertLegalTransition(req.getStatus(), "APPROVED");
        returnRequestMapper.updateStatus(id, "APPROVED");
        if (returnAddress != null && !returnAddress.isBlank()) {
            returnRequestMapper.updateReturnAddress(id, returnAddress);
        }
        String note = "审核通过" + (returnAddress != null && !returnAddress.isBlank() ? "，退货地址: " + returnAddress : "");
        recordLog(id.longValue(), req.getStatus(), "APPROVED", operatorId, role, note);
        if (req.getUserId() != null) {
            notificationService.create(req.getUserId().longValue(), "RETURN", "售后申请已通过",
                    "您的售后申请已审核通过，请按提示寄回商品。", "RETURN", id.longValue());
        }
    }

    /** 管理员/客服拒绝：PENDING → REJECTED。 */
    @Transactional
    public void reject(Integer id, String reason, Long operatorId, String role) {
        assertStaff(role);
        ReturnRequest req = requireExists(id);
        assertLegalTransition(req.getStatus(), "REJECTED");
        returnRequestMapper.updateReject(id, "REJECTED", reason);
        recordLog(id.longValue(), req.getStatus(), "REJECTED", operatorId, role, "拒绝: " + (reason == null ? "" : reason));
        if (req.getUserId() != null) {
            notificationService.create(req.getUserId().longValue(), "RETURN", "售后申请未通过",
                    "很抱歉，您的售后申请未通过审核。" + (reason != null && !reason.isBlank() ? "原因：" + reason : ""),
                    "RETURN", id.longValue());
        }
    }

    /** 管理员/客服填写退货地址（不改变状态）。 */
    @Transactional
    public void fillReturnAddress(Integer id, String returnAddress, Long operatorId, String role) {
        assertStaff(role);
        ReturnRequest req = requireExists(id);
        if (!"APPROVED".equals(req.getStatus())) {
            throw new IllegalArgumentException("仅审核通过状态可填写退货地址");
        }
        returnRequestMapper.updateReturnAddress(id, returnAddress);
        recordLog(id.longValue(), req.getStatus(), req.getStatus(), operatorId, role, "填写退货地址: " + returnAddress);
    }

    /** 客户填写快递单号：APPROVED → RETURNING。 */
    @Transactional
    public void shipBack(Integer id, String trackingNo, Long currentUserId, String role) {
        ReturnRequest req = requireExists(id);
        assertOwner(req, currentUserId, role);
        assertLegalTransition(req.getStatus(), "RETURNING");
        if (trackingNo == null || trackingNo.isBlank()) {
            throw new IllegalArgumentException("请填写快递单号");
        }
        returnRequestMapper.updateTracking(id, "RETURNING", trackingNo);
        recordLog(id.longValue(), req.getStatus(), "RETURNING", currentUserId, role, "客户寄回商品，快递单号: " + trackingNo);
    }

    /** 管理员/客服确认收货：RETURNING → RECEIVED。 */
    @Transactional
    public void confirmReceive(Integer id, Long operatorId, String role) {
        assertStaff(role);
        ReturnRequest req = requireExists(id);
        assertLegalTransition(req.getStatus(), "RECEIVED");
        returnRequestMapper.updateStatus(id, "RECEIVED");
        recordLog(id.longValue(), req.getStatus(), "RECEIVED", operatorId, role, "确认收到退货商品");
    }

    /**
     * 管理员/客服确认退款：
     * RECEIVED → REFUNDING（开始退款）或 REFUNDING → REFUNDED（完成退款，填退款金额）。
     */
    @Transactional
    public void confirmRefund(Integer id, BigDecimal refundAmount, Long operatorId, String role) {
        assertStaff(role);
        ReturnRequest req = requireExists(id);
        String from = req.getStatus();
        if ("RECEIVED".equals(from)) {
            returnRequestMapper.updateStatus(id, "REFUNDING");
            recordLog(id.longValue(), from, "REFUNDING", operatorId, role, "开始退款处理");
            if (req.getUserId() != null) {
                notificationService.create(req.getUserId().longValue(), "RETURN", "退款处理中",
                        "您的售后退款正在处理中，请耐心等待。", "RETURN", id.longValue());
            }
        } else if ("REFUNDING".equals(from)) {
            if (refundAmount == null || refundAmount.signum() <= 0) {
                throw new IllegalArgumentException("退款金额必须大于0");
            }
            Order order = req.getOrderId() == null ? null : orderService.findById(req.getOrderId().longValue());
            // 退款金额封顶：不得超过订单实付金额，防止超额退款
            BigDecimal amt = refundAmount;
            if (order != null && order.getTotalAmount() != null && amt.compareTo(order.getTotalAmount()) > 0) {
                amt = order.getTotalAmount();
            }
            returnRequestMapper.updateRefund(id, "REFUNDED", amt);
            // 退款闭环：将原订单支付流水置为已退款（模拟原路退回）；无流水则补记退款流水
            if (order != null) {
                paymentService.refundByOrder(req.getOrderId().longValue(), amt);
            }
            // 秒杀退款口径（详见 syncSeckillRefunded）：不回补库存与资格，仅同步秒杀单状态
            syncSeckillRefunded(order);
            recordLog(id.longValue(), from, "REFUNDED", operatorId, role, "完成退款 ¥" + amt);
            if (req.getUserId() != null) {
                notificationService.create(req.getUserId().longValue(), "RETURN", "退款已完成",
                        "您的售后退款 ¥" + amt + " 已原路退回，请注意查收。", "RETURN", id.longValue());
            }
        } else {
            throw new IllegalArgumentException("当前状态不可退款: " + from);
        }
    }

    /** 管理员/客服关闭售后：任意非终态 → CLOSED。 */
    @Transactional
    public void close(Integer id, String reason, Long operatorId, String role) {
        assertStaff(role);
        ReturnRequest req = requireExists(id);
        if (TERMINAL_STATUSES.contains(req.getStatus())) {
            throw new IllegalArgumentException("当前状态不可关闭: " + req.getStatus());
        }
        String from = req.getStatus();
        returnRequestMapper.updateStatus(id, "CLOSED");
        recordLog(id.longValue(), from, "CLOSED", operatorId, role, "关闭售后" + (reason != null && !reason.isBlank() ? ": " + reason : ""));
    }

    // ===================== 内部工具 =====================

    /**
     * 秒杀退款口径（显式化）：支付成功即视为抢购资格已消耗。
     * 已支付秒杀订单退款：不回补活动库存、不回退抢购资格（该活动不可再抢）——
     * 防止「抢到 → 秒退 → 再抢」套利，且活动「限量 N 件」承诺不因售后退款失真；
     * 仅联动秒杀单状态 PAID → REFUNDED，供「我的秒杀单」如实展示。
     * 注意区分：未支付超时取消的库存/资格回补由 SeckillTimeoutConsumer 负责，与退款路径互不影响。
     */
    private void syncSeckillRefunded(Order order) {
        if (order == null || !"seckill".equals(order.getPayType())) {
            return;
        }
        seckillOrderMapper.markRefundedByConvertOrderNo(order.getOrderNo());
    }

    private ReturnRequest requireExists(Integer id) {
        ReturnRequest req = returnRequestMapper.findById(id);
        if (req == null) throw new IllegalArgumentException("售后单不存在");
        return req;
    }

    private void assertOwner(ReturnRequest req, Long userId, String role) {
        if ("customer".equals(role) && !userId.equals(req.getUserId().longValue())) {
            throw new IllegalArgumentException("无权操作他人售后单");
        }
    }

    private void assertStaff(String role) {
        if ("customer".equals(role)) {
            throw new IllegalArgumentException("仅管理员/客服可执行此操作");
        }
    }

    private void assertLegalTransition(String from, String to) {
        Set<String> targets = LEGAL_TRANSITIONS.get(from);
        if (targets == null || !targets.contains(to)) {
            throw new IllegalArgumentException("非法状态流转: " + from + " → " + to);
        }
    }

    private void recordLog(Long returnRequestId, String fromStatus, String toStatus,
                           Long operatorId, String role, String note) {
        ReturnRequestLog log = new ReturnRequestLog();
        log.setReturnRequestId(returnRequestId);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorId(operatorId);
        log.setOperatorRole(role);
        log.setNote(note);
        logMapper.insert(log);
    }
}

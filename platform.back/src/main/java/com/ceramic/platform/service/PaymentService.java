package com.ceramic.platform.service;

import com.ceramic.platform.entity.Customization;
import com.ceramic.platform.entity.Order;
import com.ceramic.platform.entity.Payment;
import com.ceramic.platform.entity.SeckillOrder;
import com.ceramic.platform.mapper.PaymentMapper;
import com.ceramic.platform.mapper.SeckillOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟支付网关。
 *
 * 链路：发起支付(PENDING) → 模拟网关回调(SUCCESS) → 按业务类型推进业务状态。
 * 支持四类业务：ORDER(订单) / CUSTOM_DEPOSIT(定制定金) / CUSTOM_BALANCE(定制尾款) / SECKILL(秒杀单)。
 * 回调用条件更新保证幂等，重复回调不会重复触发扣库存等副作用。
 */
@Service
public class PaymentService {

    private static final Set<String> BIZ_TYPES = Set.of("ORDER", "CUSTOM_DEPOSIT", "CUSTOM_BALANCE", "SECKILL");
    private static final Set<String> CHANNELS = Set.of("alipay", "wechat", "bank");

    private final PaymentMapper paymentMapper;
    private final OrderService orderService;
    private final CustomizationService customizationService;
    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillService seckillService;

    public PaymentService(PaymentMapper paymentMapper, OrderService orderService,
                          CustomizationService customizationService, SeckillOrderMapper seckillOrderMapper,
                          SeckillService seckillService) {
        this.paymentMapper = paymentMapper;
        this.orderService = orderService;
        this.customizationService = customizationService;
        this.seckillOrderMapper = seckillOrderMapper;
        this.seckillService = seckillService;
    }

    public Payment findByPaymentNo(String paymentNo) {
        Payment p = paymentMapper.findByPaymentNo(paymentNo);
        if (p == null) throw new IllegalArgumentException("支付流水不存在");
        return p;
    }

    public List<Payment> listByUser(Long userId) {
        return paymentMapper.findByUserId(userId);
    }

    /**
     * 发起支付：校验业务单归属与可支付性，落一条 PENDING 流水。
     * 应付金额取自业务单当前真实值，不信任前端传入。
     */
    @Transactional
    public Payment initiate(String bizType, Long bizId, String channel, Long userId, String role) {
        if (bizType == null || !BIZ_TYPES.contains(bizType)) {
            throw new IllegalArgumentException("不支持的支付业务类型");
        }
        if (bizId == null) {
            throw new IllegalArgumentException("业务单ID不能为空");
        }
        if (channel == null || !CHANNELS.contains(channel)) {
            channel = "alipay";
        }

        BigDecimal amount;
        switch (bizType) {
            case "ORDER" -> {
                Order order = orderService.findById(bizId);
                if (order == null || !userId.equals(order.getUserId())) {
                    throw new IllegalArgumentException("订单不存在或无权支付");
                }
                if (!"PENDING_PAY".equalsIgnoreCase(order.getStatus())) {
                    throw new IllegalArgumentException("订单当前状态不可支付: " + order.getStatus());
                }
                amount = order.getTotalAmount();
            }
            case "CUSTOM_DEPOSIT" -> {
                Customization c = customizationService.requireDepositPayable(bizId, userId, role);
                amount = c.getDepositAmount();
            }
            case "CUSTOM_BALANCE" -> {
                Customization c = customizationService.requireBalancePayable(bizId, userId, role);
                amount = c.getFinalAmount();
            }
            case "SECKILL" -> {
                SeckillOrder so = seckillOrderMapper.findById(bizId);
                if (so == null || !userId.equals(so.getUserId())) {
                    throw new IllegalArgumentException("秒杀订单不存在或无权支付");
                }
                if (!"PENDING".equalsIgnoreCase(so.getStatus())) {
                    throw new IllegalArgumentException("秒杀订单当前状态不可支付: " + so.getStatus());
                }
                amount = so.getPayAmount();
            }
            default -> throw new IllegalArgumentException("不支持的支付业务类型");
        }

        // 去重：同一业务单已存在本人的待支付流水时直接复用，避免重复发起产生多条 PENDING
        Payment existing = paymentMapper.findLatestPendingByBiz(bizType, bizId);
        if (existing != null && userId.equals(existing.getUserId())) {
            return existing;
        }

        Payment p = new Payment();
        p.setPaymentNo(genNo("PAY"));
        p.setBizType(bizType);
        p.setBizId(bizId);
        p.setUserId(userId);
        p.setAmount(amount);
        p.setChannel(channel);
        p.setStatus("PENDING");
        paymentMapper.insert(p);
        return p;
    }

    /**
     * 模拟网关回调「支付成功」。幂等：仅 PENDING → SUCCESS 的那一次触发业务副作用。
     */
    @Transactional
    public Payment callbackSuccess(String paymentNo, Long userId, String role) {
        Payment p = findByPaymentNo(paymentNo);
        if ("customer".equals(role) && !userId.equals(p.getUserId())) {
            throw new IllegalArgumentException("无权操作他人支付流水");
        }
        String transactionId = genNo("TXN");
        int rows = paymentMapper.markSuccess(paymentNo, transactionId);
        if (rows == 0) {
            // 已被处理过（重复回调），直接返回最新状态，不重复触发副作用
            return findByPaymentNo(paymentNo);
        }
        // 推进业务状态
        switch (p.getBizType()) {
            case "ORDER" -> {
                // 若订单已由其他入口支付（非待支付），流水置 SUCCESS 即可，不再重复推进以免触发非法状态流转
                Order order = orderService.findById(p.getBizId());
                if (order != null && "PENDING_PAY".equalsIgnoreCase(order.getStatus())) {
                    orderService.updateStatus(p.getBizId(), "PAID");
                }
            }
            case "CUSTOM_DEPOSIT" -> customizationService.markDepositPaidByPayment(p.getBizId());
            case "CUSTOM_BALANCE" -> customizationService.markBalancePaidByPayment(p.getBizId());
            case "SECKILL" -> {
                // CAS（PENDING → PAID）与超时取消互斥：已被取消则整体回滚（流水也不落 SUCCESS）
                int seckillRows = seckillOrderMapper.updateStatusGuarded(p.getBizId(), "PAID", "PENDING");
                if (seckillRows == 0) {
                    throw new IllegalArgumentException("秒杀订单已超时取消，无法支付");
                }
                // 同事务转正式订单：履约（补地址/发货/收货/售后）统一走订单域；失败整体回滚
                SeckillOrder so = seckillOrderMapper.findById(p.getBizId());
                seckillService.convertToOrder(so);
            }
            default -> throw new IllegalArgumentException("不支持的支付业务类型");
        }
        return findByPaymentNo(paymentNo);
    }

    /**
     * 模拟收银台「确认支付」：顾客在模拟网关完成付款后由服务端确认入账。
     * 与 callbackSuccess 共用幂等推进逻辑；确认前复检业务单当前状态，
     * 避免为已超时取消的订单或已推进的定制单重复入账。
     */
    @Transactional
    public Payment confirmAtGateway(String paymentNo, Long userId, String role) {
        Payment p = findByPaymentNo(paymentNo);
        if ("customer".equals(role) && !userId.equals(p.getUserId())) {
            throw new IllegalArgumentException("无权操作他人支付流水");
        }
        if (!"PENDING".equals(p.getStatus())) {
            return p;
        }
        switch (p.getBizType()) {
            case "ORDER" -> {
                Order order = orderService.findById(p.getBizId());
                if (order == null || !"PENDING_PAY".equalsIgnoreCase(order.getStatus())) {
                    throw new IllegalArgumentException("订单已支付或已取消，无法继续支付");
                }
            }
            case "CUSTOM_DEPOSIT" -> customizationService.requireDepositPayable(p.getBizId(), userId, role);
            case "CUSTOM_BALANCE" -> customizationService.requireBalancePayable(p.getBizId(), userId, role);
            case "SECKILL" -> {
                SeckillOrder so = seckillOrderMapper.findById(p.getBizId());
                if (so == null || !"PENDING".equalsIgnoreCase(so.getStatus())) {
                    throw new IllegalArgumentException("秒杀订单已支付或已取消，无法继续支付");
                }
            }
            default -> throw new IllegalArgumentException("不支持的支付业务类型");
        }
        return callbackSuccess(paymentNo, userId, role);
    }

    /** 手动退款（管理员/客服）：按支付流水号原路退回。退款金额不得超过实付金额。 */
    @Transactional
    public Payment refund(String paymentNo, BigDecimal refundAmount) {
        Payment p = findByPaymentNo(paymentNo);
        if (!"SUCCESS".equals(p.getStatus())) {
            throw new IllegalArgumentException("仅已支付成功的流水可退款，当前状态: " + p.getStatus());
        }
        BigDecimal amt = capRefund(refundAmount, p.getAmount());
        paymentMapper.markRefunded(p.getId(), genNo("RFD"), amt);
        return findByPaymentNo(paymentNo);
    }

    /**
     * 退款闭环对接：按订单原路退回，退款金额以实付金额为上限。
     * 找到成功流水则回写为已退款；历史订单若无成功流水（经旧的直接支付入口完成），
     * 则补记一条「已支付→已退款」的流水，保证退款有据可查、对账可追溯，而非静默跳过。
     */
    @Transactional
    public void refundByOrder(Long orderId, BigDecimal refundAmount) {
        Payment p = paymentMapper.findLatestSuccessByBiz("ORDER", orderId);
        if (p != null) {
            paymentMapper.markRefunded(p.getId(), genNo("RFD"), capRefund(refundAmount, p.getAmount()));
            return;
        }
        Order order = orderService.findById(orderId);
        if (order == null || refundAmount == null || refundAmount.signum() <= 0) return;
        BigDecimal amt = capRefund(refundAmount, order.getTotalAmount());
        Payment r = new Payment();
        r.setPaymentNo(genNo("PAY"));
        r.setBizType("ORDER");
        r.setBizId(orderId);
        r.setUserId(order.getUserId());
        r.setAmount(amt);
        r.setChannel("alipay");
        r.setTransactionId(genNo("TXN"));
        paymentMapper.insertSuccess(r);
        paymentMapper.markRefunded(r.getId(), genNo("RFD"), amt);
    }

    /** 退款金额封顶：非正数时退全额，超过实付时按实付封顶。 */
    private BigDecimal capRefund(BigDecimal requested, BigDecimal paid) {
        if (requested == null || requested.signum() <= 0) return paid;
        return requested.compareTo(paid) > 0 ? paid : requested;
    }

    /** 生成流水号/交易号/退款号，供本服务及兼容旧入口的其他服务（静态调用，避免形成 Bean 循环依赖）。 */
    public static String genNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}

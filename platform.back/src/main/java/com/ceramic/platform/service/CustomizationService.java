package com.ceramic.platform.service;

import com.ceramic.platform.entity.Customization;
import com.ceramic.platform.entity.CustomizationProgress;
import com.ceramic.platform.entity.Payment;
import com.ceramic.platform.mapper.CustomizationMapper;
import com.ceramic.platform.mapper.CustomizationProgressMapper;
import com.ceramic.platform.mapper.PaymentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 陶瓷定制业务服务。
 *
 * 统一状态机：PENDING → QUOTED → CONFIRMED → IN_PROGRESS → QUALITY_CHECK → COMPLETED
 * 任意非终态可 → CANCELLED；QUOTED 可由客户 → REJECTED；PENDING/QUOTED 可由管理员 → REJECTED。
 * 终态：COMPLETED、CANCELLED、REJECTED。
 */
@Service
public class CustomizationService {

    private static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.3");

    /** 合法状态流转映射（from → 允许到达的 to 集合）。 */
    private static final Map<String, Set<String>> LEGAL_TRANSITIONS = Map.of(
            "PENDING", Set.of("QUOTED", "REJECTED", "CANCELLED"),
            "QUOTED", Set.of("CONFIRMED", "REJECTED", "CANCELLED"),
            "CONFIRMED", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("QUALITY_CHECK", "CANCELLED"),
            "QUALITY_CHECK", Set.of("COMPLETED", "IN_PROGRESS"),
            "COMPLETED", Set.of(),
            "CANCELLED", Set.of(),
            "REJECTED", Set.of()
    );

    private static final Set<String> TERMINAL_STATUSES = Set.of("COMPLETED", "CANCELLED", "REJECTED");

    private final CustomizationMapper customizationMapper;
    private final CustomizationProgressMapper progressMapper;
    private final PaymentMapper paymentMapper;
    private final NotificationService notificationService;

    public CustomizationService(CustomizationMapper customizationMapper, CustomizationProgressMapper progressMapper,
                                PaymentMapper paymentMapper, NotificationService notificationService) {
        this.customizationMapper = customizationMapper;
        this.progressMapper = progressMapper;
        this.paymentMapper = paymentMapper;
        this.notificationService = notificationService;
    }

    public List<Customization> list(Long userId) {
        return userId == null ? customizationMapper.findAll() : customizationMapper.findByUserId(userId);
    }

    public Customization findById(Long id) {
        return customizationMapper.findById(id);
    }

    @Transactional
    public Customization create(Customization customization) {
        if (customization.getContactName() == null || customization.getContactName().isBlank()) {
            throw new IllegalArgumentException("联系人不能为空");
        }
        if (customization.getContactPhone() == null || customization.getContactPhone().isBlank()) {
            throw new IllegalArgumentException("联系电话不能为空");
        }
        if (customization.getShippingAddress() == null || customization.getShippingAddress().isBlank()) {
            throw new IllegalArgumentException("收货地址不能为空");
        }
        if (customization.getQuantity() == null || customization.getQuantity() <= 0) {
            customization.setQuantity(1);
        }
        customization.setStatus("PENDING");
        customizationMapper.insert(customization);
        // 创建即记录首条进度
        recordProgress(customization.getId(), "提交定制", null, "PENDING",
                customization.getUserId(), "customer", "客户提交定制申请", null);
        return customization;
    }

    /** 管理员报价：PENDING → QUOTED。 */
    @Transactional
    public void quote(Long id, BigDecimal quotedPrice, String expectedCompleteDate,
                      String timelineNote, Long operatorId, String role) {
        if (!"admin".equals(role)) {
            throw new IllegalArgumentException("仅管理员可报价");
        }
        Customization c = requireExists(id);
        if (!"PENDING".equals(c.getStatus())) {
            throw new IllegalArgumentException("仅待报价状态可报价，当前状态: " + c.getStatus());
        }
        if (quotedPrice == null || quotedPrice.signum() <= 0) {
            throw new IllegalArgumentException("报价必须大于 0");
        }
        BigDecimal deposit = quotedPrice.multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalAmount = quotedPrice.subtract(deposit);
        customizationMapper.updateQuote(id, quotedPrice, deposit, finalAmount, timelineNote, expectedCompleteDate);
        String desc = "管理员报价 ¥" + quotedPrice + "，定金 ¥" + deposit + "，尾款 ¥" + finalAmount
                + (expectedCompleteDate != null ? "，预计完成 " + expectedCompleteDate : "");
        recordProgress(id, "QUOTED", "PENDING", "QUOTED", operatorId, role, desc, null);
        notificationService.create(c.getUserId(), "CUSTOMIZATION", "定制已报价",
                "您的定制单已报价 ¥" + quotedPrice + "（定金 ¥" + deposit + "），请确认。", "CUSTOMIZATION", id);
    }

    /** 客户确认报价：QUOTED → CONFIRMED。 */
    @Transactional
    public void confirmQuote(Long id, Long userId, String role) {
        Customization c = requireExists(id);
        assertOwner(c, userId, role);
        if (!"QUOTED".equals(c.getStatus())) {
            throw new IllegalArgumentException("仅已报价状态可确认，当前状态: " + c.getStatus());
        }
        customizationMapper.updateStatus(id, "CONFIRMED");
        recordProgress(id, "CONFIRMED", "QUOTED", "CONFIRMED", userId, role, "客户确认报价", null);
    }

    /** 客户支付定金：状态须为 CONFIRMED 且尚未支付。仅置 deposit_paid，不改变状态。 */
    @Transactional
    public void payDeposit(Long id, Long userId, String role) {
        Customization c = requireExists(id);
        assertOwner(c, userId, role);
        if (!"CONFIRMED".equals(c.getStatus())) {
            throw new IllegalArgumentException("仅已确认报价的定制单可支付定金，当前状态: " + c.getStatus());
        }
        if (Boolean.TRUE.equals(c.getDepositPaid())) {
            throw new IllegalArgumentException("定金已支付，请勿重复支付");
        }
        if (c.getDepositAmount() == null || c.getDepositAmount().signum() <= 0) {
            throw new IllegalArgumentException("定金金额未生成，无法支付");
        }
        customizationMapper.markDepositPaid(id);
        recordProgress(id, "支付定金", "CONFIRMED", "CONFIRMED", userId, role,
                "客户支付定金 ¥" + c.getDepositAmount(), null);
        // 兼容旧的「直接支付定金」入口：补记一条成功支付流水，与网关支付保持一致、可对账
        recordCustomFlow(c, "CUSTOM_DEPOSIT", c.getDepositAmount());
    }

    // ===================== 支付网关对接（Phase A/B） =====================

    /** 校验定金可支付并返回单据（供支付网关发起流水时读取应付金额），不改状态。 */
    public Customization requireDepositPayable(Long id, Long userId, String role) {
        Customization c = requireExists(id);
        assertOwner(c, userId, role);
        if (!"CONFIRMED".equals(c.getStatus())) {
            throw new IllegalArgumentException("仅已确认报价的定制单可支付定金，当前状态: " + c.getStatus());
        }
        if (Boolean.TRUE.equals(c.getDepositPaid())) {
            throw new IllegalArgumentException("定金已支付，请勿重复支付");
        }
        if (c.getDepositAmount() == null || c.getDepositAmount().signum() <= 0) {
            throw new IllegalArgumentException("定金金额未生成，无法支付");
        }
        return c;
    }

    /** 校验尾款可支付并返回单据（供支付网关发起流水时读取应付金额），不改状态。 */
    public Customization requireBalancePayable(Long id, Long userId, String role) {
        Customization c = requireExists(id);
        assertOwner(c, userId, role);
        if (!"QUALITY_CHECK".equals(c.getStatus())) {
            throw new IllegalArgumentException("仅质检完成待验收的定制单可支付尾款，当前状态: " + c.getStatus());
        }
        if (!Boolean.TRUE.equals(c.getDepositPaid())) {
            throw new IllegalArgumentException("定金尚未支付，无法支付尾款");
        }
        if (Boolean.TRUE.equals(c.getFinalPaid())) {
            throw new IllegalArgumentException("尾款已支付，请勿重复支付");
        }
        if (c.getFinalAmount() == null || c.getFinalAmount().signum() <= 0) {
            throw new IllegalArgumentException("尾款金额未生成，无法支付");
        }
        return c;
    }

    /** 支付网关回调驱动：标记定金已付（幂等校验在网关流水层完成）。 */
    @Transactional
    public void markDepositPaidByPayment(Long id) {
        Customization c = requireExists(id);
        if (Boolean.TRUE.equals(c.getDepositPaid())) {
            return;
        }
        customizationMapper.markDepositPaid(id);
        recordProgress(id, "支付定金", c.getStatus(), c.getStatus(), c.getUserId(), "customer",
                "客户支付定金 ¥" + c.getDepositAmount(), null);
    }

    /** 支付网关回调驱动：标记尾款已付。 */
    @Transactional
    public void markBalancePaidByPayment(Long id) {
        Customization c = requireExists(id);
        if (Boolean.TRUE.equals(c.getFinalPaid())) {
            return;
        }
        customizationMapper.markBalancePaid(id);
        recordProgress(id, "支付尾款", c.getStatus(), c.getStatus(), c.getUserId(), "customer",
                "客户支付尾款 ¥" + c.getFinalAmount(), null);
    }

    /** 客户验收：QUALITY_CHECK → COMPLETED，须已支付尾款。与「定金闸门」对称。 */
    @Transactional
    public void accept(Long id, Long userId, String role) {
        Customization c = requireExists(id);
        assertOwner(c, userId, role);
        if (!"QUALITY_CHECK".equals(c.getStatus())) {
            throw new IllegalArgumentException("仅质检完成的定制单可验收，当前状态: " + c.getStatus());
        }
        if (!Boolean.TRUE.equals(c.getFinalPaid())) {
            throw new IllegalArgumentException("尚未支付尾款，无法验收");
        }
        customizationMapper.updateStatus(id, "COMPLETED");
        recordProgress(id, "COMPLETED", "QUALITY_CHECK", "COMPLETED", userId, role, "客户确认验收，定制完成", null);
        notificationService.create(c.getUserId(), "CUSTOMIZATION", "定制已完成",
                "您的定制单已验收完成，感谢您的信赖！", "CUSTOMIZATION", id);
    }

    /** 客户/管理员拒绝报价：QUOTED → REJECTED。 */
    @Transactional
    public void rejectQuote(Long id, Long userId, String role, String reason) {
        Customization c = requireExists(id);
        assertOwner(c, userId, role);
        if (!"QUOTED".equals(c.getStatus())) {
            throw new IllegalArgumentException("仅已报价状态可拒绝，当前状态: " + c.getStatus());
        }
        customizationMapper.updateStatus(id, "REJECTED");
        String desc = "拒绝报价" + (reason != null && !reason.isBlank() ? "：" + reason : "");
        recordProgress(id, "REJECTED", "QUOTED", "REJECTED", userId, role, desc, null);
    }

    /** 取消定制：任意非终态 → CANCELLED。客户仅可取消自己的，管理员可取消全部。 */
    @Transactional
    public void cancel(Long id, Long userId, String role, String reason) {
        Customization c = requireExists(id);
        assertOwner(c, userId, role);
        if (TERMINAL_STATUSES.contains(c.getStatus())) {
            throw new IllegalArgumentException("当前状态不可取消: " + c.getStatus());
        }
        String from = c.getStatus();
        customizationMapper.updateStatus(id, "CANCELLED");
        String desc = "取消定制" + (reason != null && !reason.isBlank() ? "：" + reason : "");
        recordProgress(id, "CANCELLED", from, "CANCELLED", userId, role, desc, null);
        // 管理员/客服代为取消时通知客户；客户本人取消则无需自我提醒
        if (!"customer".equals(role)) {
            notificationService.create(c.getUserId(), "CUSTOMIZATION", "定制单已取消",
                    "您的定制单已被取消。" + (reason != null && !reason.isBlank() ? "原因：" + reason : ""),
                    "CUSTOMIZATION", id);
        }
    }

    /**
     * 管理员通用状态流转。仅允许合法跳转，禁止任意跳转。
     * 完成（→COMPLETED）时可附带成品图。
     */
    @Transactional
    public void transitionStatus(Long id, String toStatus, String note, String imageUrl,
                                 Long operatorId, String role) {
        if (!"admin".equals(role)) {
            throw new IllegalArgumentException("仅管理员可更新定制状态");
        }
        Customization c = requireExists(id);
        String from = c.getStatus();
        if (!isLegalTransition(from, toStatus)) {
            throw new IllegalArgumentException("非法状态流转: " + from + " → " + toStatus);
        }
        // 开始制作前必须已收取定金，形成「报价→确认→付定金→制作」闭环
        if ("CONFIRMED".equals(from) && "IN_PROGRESS".equals(toStatus) && !Boolean.TRUE.equals(c.getDepositPaid())) {
            throw new IllegalArgumentException("客户尚未支付定金，无法开始制作");
        }
        if ("COMPLETED".equals(toStatus) && imageUrl != null && !imageUrl.isBlank()) {
            customizationMapper.updateStatusAndFinished(id, toStatus, imageUrl);
            c.setFinishedProductUrl(imageUrl);
        } else {
            customizationMapper.updateStatus(id, toStatus);
        }
        String desc = note != null && !note.isBlank() ? note : ("状态变更: " + from + " → " + toStatus);
        recordProgress(id, toStatus, from, toStatus, operatorId, role, desc, imageUrl);
        if ("QUALITY_CHECK".equals(toStatus)) {
            String amt = c.getFinalAmount() != null ? "¥" + c.getFinalAmount() : "尾款";
            notificationService.create(c.getUserId(), "CUSTOMIZATION", "定制成品待验收",
                    "您的定制成品已完成质检，请支付尾款（" + amt + "）后确认验收。", "CUSTOMIZATION", id);
        }
    }

    /** 管理员添加制作阶段进度说明（不改变状态）。 */
    @Transactional
    public void addProgress(Long customizationId, String stage, String description, String imageUrl,
                            Long operatorId, String role) {
        if (!"admin".equals(role)) {
            throw new IllegalArgumentException("仅管理员可添加进度");
        }
        Customization c = requireExists(customizationId);
        String current = c.getStatus();
        recordProgress(customizationId, stage, current, current, operatorId, role, description, imageUrl);
    }

    public List<CustomizationProgress> getProgress(Long customizationId) {
        return progressMapper.findByCustomizationId(customizationId);
    }

    // ===================== 内部工具 =====================

    private Customization requireExists(Long id) {
        Customization c = customizationMapper.findById(id);
        if (c == null) throw new IllegalArgumentException("定制单不存在");
        return c;
    }

    /** 消费者仅可操作自己的定制单；管理员可操作全部。 */
    private void assertOwner(Customization c, Long userId, String role) {
        if ("customer".equals(role) && !userId.equals(c.getUserId())) {
            throw new IllegalArgumentException("无权操作他人定制单");
        }
    }

    private boolean isLegalTransition(String from, String to) {
        Set<String> targets = LEGAL_TRANSITIONS.get(from);
        return targets != null && targets.contains(to);
    }

    private void recordProgress(Long customizationId, String stage, String fromStatus, String toStatus,
                                Long operatorId, String role, String description, String imageUrl) {
        CustomizationProgress p = new CustomizationProgress();
        p.setCustomizationId(customizationId);
        p.setStage(stage);
        p.setDescription(description);
        p.setImageUrl(imageUrl);
        p.setOperatorId(operatorId);
        p.setOperatorRole(role);
        p.setFromStatus(fromStatus);
        p.setToStatus(toStatus);
        progressMapper.insert(p);
    }

    /** 兼容旧的直接支付入口：为定制定金/尾款补记一条「支付成功」流水，与网关支付保持一致、可对账。 */
    private void recordCustomFlow(Customization c, String bizType, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        Payment p = new Payment();
        p.setPaymentNo(PaymentService.genNo("PAY"));
        p.setBizType(bizType);
        p.setBizId(c.getId());
        p.setUserId(c.getUserId());
        p.setAmount(amount);
        p.setChannel("alipay");
        p.setTransactionId(PaymentService.genNo("TXN"));
        paymentMapper.insertSuccess(p);
    }
}

package com.ceramic.platform.service;

import com.ceramic.platform.dto.CreateOrderRequest;
import com.ceramic.platform.entity.CartItem;
import com.ceramic.platform.entity.Order;
import com.ceramic.platform.entity.OrderItem;
import com.ceramic.platform.entity.Payment;
import com.ceramic.platform.entity.Product;
import com.ceramic.platform.mapper.CartMapper;
import com.ceramic.platform.mapper.OrderMapper;
import com.ceramic.platform.mapper.PaymentMapper;
import com.ceramic.platform.mapper.ProductMapper;
import com.ceramic.platform.mapper.SeckillOrderMapper;
import com.ceramic.platform.mq.OrderTimeoutPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderService {
    private final OrderMapper orderMapper;
    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final PaymentMapper paymentMapper;
    private final UserBehaviorService userBehaviorService;
    private final NotificationService notificationService;
    private final OrderTimeoutPublisher orderTimeoutPublisher;
    private final SeckillOrderMapper seckillOrderMapper;

    public OrderService(OrderMapper orderMapper, CartMapper cartMapper, ProductMapper productMapper,
                        PaymentMapper paymentMapper, UserBehaviorService userBehaviorService,
                        NotificationService notificationService, OrderTimeoutPublisher orderTimeoutPublisher,
                        SeckillOrderMapper seckillOrderMapper) {
        this.orderMapper = orderMapper;
        this.cartMapper = cartMapper;
        this.productMapper = productMapper;
        this.paymentMapper = paymentMapper;
        this.userBehaviorService = userBehaviorService;
        this.notificationService = notificationService;
        this.orderTimeoutPublisher = orderTimeoutPublisher;
        this.seckillOrderMapper = seckillOrderMapper;
    }

    public List<Order> list(Long userId) {
        return userId == null ? orderMapper.findAll() : orderMapper.findByUserId(userId);
    }

    public Order findById(Long id) {
        return orderMapper.findById(id);
    }

    public Map<String, Object> detail(Long orderId) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("order", orderMapper.findById(orderId));
        detail.put("items", orderMapper.findItems(orderId));
        return detail;
    }

    public List<OrderItem> findItems(Long orderId) {
        return orderMapper.findItems(orderId);
    }

    @Transactional
    public Order createFromCart(CreateOrderRequest request) {
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        if (request.getReceiverPhone() == null || !request.getReceiverPhone().trim().matches("^1[3-9]\\d{9}$")) {
            throw new IllegalArgumentException("收货人电话必须为 11 位手机号");
        }
        List<CartItem> cartItems = cartMapper.findByUserId(request.getUserId());
        // 勾选结算：只取用户勾选的购物车项（findByUserId 保证归属，再按 id 过滤）
        if (request.getCartItemIds() != null && !request.getCartItemIds().isEmpty()) {
            cartItems = cartItems.stream()
                    .filter(ci -> request.getCartItemIds().contains(ci.getId()))
                    .toList();
        }
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("请选择要结算的商品");
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            // 以下单时的最新商品信息为准：避免购物车中的历史价格与商家改价后的真实价格不一致
            Product product = productMapper.findById(cartItem.getProductId());
            if (product == null || !"active".equals(product.getStatus())) {
                throw new IllegalArgumentException("商品已下架，无法下单：" + cartItem.getProductName());
            }
            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            total = total.add(subtotal);

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setImageUrl(product.getImageUrl());
            item.setUnitPrice(unitPrice);
            item.setQuantity(cartItem.getQuantity());
            item.setSubtotal(subtotal);
            orderItems.add(item);
        }

        Order order = new Order();
        order.setOrderNo(nextOrderNo());
        order.setUserId(request.getUserId());
        order.setTotalAmount(total);
        order.setStatus("PENDING_PAY");
        order.setPayType(request.getPayType());
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());
        order.setReceiverAddress(request.getReceiverAddress());
        order.setRemark(request.getRemark());
        orderMapper.insert(order);

        for (OrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderMapper.insertItem(item);
        }
        // 只清除本次已结算的购物车项；未勾选的保留在购物车（归属以 findByUserId 为准，防越权删他人项）
        if (request.getCartItemIds() != null && !request.getCartItemIds().isEmpty()) {
            for (CartItem ci : cartItems) {
                cartMapper.delete(ci.getId());
            }
        } else {
            cartMapper.clearByUserId(request.getUserId());
        }
        // 投递超时延迟消息（发送失败不影响下单，定时任务兜底）。TTL 到期由消费者 CAS 取消。
        orderTimeoutPublisher.publish(order.getId(), order.getOrderNo());
        return order;
    }

    /**
     * 推进订单状态（带 CAS 闸门）。
     * @return true=本线程成功抢到流转；false=状态已被其它线程改动（重复提交/并发），未执行任何副作用
     */
    @Transactional
    public boolean updateStatus(Long id, String requestedStatus) {
        Order order = orderMapper.findById(id);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        String current = normalizeStatus(order.getStatus());
        String target = normalizeStatus(requestedStatus);
        if (!allowedTransitions().getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalArgumentException("订单状态不能从 " + current + " 变更为 " + target);
        }
        // 关键并发保护：先用「带原状态条件的更新」作为唯一闸门。
        // order.getStatus() 是刚从库里读到的原状态；只有当它没被其它线程改动时更新才成功(rows=1)。
        // 若用户重复点击/并发支付，另一个线程会拿到 rows=0，直接拒绝，从而不会重复扣库存、重复记流水。
        int rows = orderMapper.updateStatusGuarded(id, target, order.getStatus());
        if (rows == 0) {
            return false;
        }
        List<OrderItem> items = orderMapper.findItems(id);
        if ("PAID".equals(target)) {
            for (OrderItem item : items) {
                if (productMapper.decreaseStock(item.getProductId(), item.getQuantity()) == 0) {
                    throw new IllegalArgumentException("商品库存不足：" + item.getProductName());
                }
                userBehaviorService.recordBehavior(order.getUserId(), item.getProductId(), "purchase");
            }
        }
        if ("CANCELLED".equals(target) && !"PENDING_PAY".equals(current)) {
            for (OrderItem item : items) productMapper.increaseStock(item.getProductId(), item.getQuantity());
        }
        notifyOrderStatus(order, target);
        return true;
    }

    @Transactional
    public void pay(Long orderId, Long userId) {
        Order order = orderMapper.findById(orderId);
        if (order == null || !userId.equals(order.getUserId())) throw new IllegalArgumentException("订单不存在或无权支付");
        // 状态推进失败（订单恰好被超时任务取消/已被其它入口支付）必须终止，
        // 不能为无效订单记「支付成功」流水，否则资金流水与订单状态不一致
        if (!updateStatus(orderId, "PAID")) {
            throw new IllegalArgumentException("订单已取消或已支付，无法支付");
        }
        // 兼容旧的「直接支付」入口：补记一条成功支付流水，保证与网关支付一致、可对账、可原路退款
        recordSuccessFlow(order);
    }

    /** 管理员/客服发货：仅已支付订单可发货，记录快递公司与单号，状态置为 SHIPPED。 */
    @Transactional
    public void ship(Long orderId, String shippingCompany, String trackingNo) {
        Order order = orderMapper.findById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        String current = normalizeStatus(order.getStatus());
        if (!"PAID".equals(current)) {
            throw new IllegalArgumentException("仅已支付的订单可发货，当前状态：" + current);
        }
        if (shippingCompany == null || shippingCompany.isBlank()) throw new IllegalArgumentException("请填写快递公司");
        if (trackingNo == null || trackingNo.isBlank()) throw new IllegalArgumentException("请填写快递单号");
        orderMapper.ship(orderId, shippingCompany.trim(), trackingNo.trim());
        notificationService.create(order.getUserId(), "ORDER", "订单已发货",
                "您的订单 " + order.getOrderNo() + " 已发货：" + shippingCompany.trim() + " " + trackingNo.trim(),
                "ORDER", orderId);
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId, String role) {
        Order order = orderMapper.findById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if ("customer".equals(role) && !userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("无权取消他人订单");
        }
        String current = normalizeStatus(order.getStatus());
        if ("customer".equals(role)) {
            // 顾客只能取消未支付订单；已付款订单须走「售后申请」退款，避免绕过退款流程直接丢单丢钱
            if (!"PENDING_PAY".equals(current)) {
                throw new IllegalArgumentException("已付款订单请通过「售后申请」退款，不能直接取消");
            }
        } else if (!"PENDING_PAY".equals(current) && !"PAID".equals(current)) {
            throw new IllegalArgumentException("当前订单状态(" + current + ")不可取消");
        }
        boolean wasPaid = "PAID".equals(current);
        updateStatus(orderId, "CANCELLED");
        if (wasPaid) {
            // 管理员/客服取消已付款订单：同步补记退款流水，保证资金可追溯
            refundSuccessFlow(order);
            if ("seckill".equals(order.getPayType())) {
                // 秒杀单联动：管理员直接取消已付款秒杀单时，同步秒杀侧状态为 REFUNDED（CAS 幂等），与售后退款口径一致
                seckillOrderMapper.markRefundedByConvertOrderNo(order.getOrderNo());
            }
        }
    }

    @Transactional
    public void confirmReceipt(Long orderId, Long userId, String role) {        Order order = orderMapper.findById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if ("customer".equals(role) && !userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("无权操作他人订单");
        }
        String current = normalizeStatus(order.getStatus());
        if (!"SHIPPED".equals(current)) {
            throw new IllegalArgumentException("仅已发货的订单可确认收货");
        }
        updateStatus(orderId, "COMPLETED");
    }

    /** 顾客修改收货信息：仅本人订单、且未发货（待支付/已支付）时可改；发货后收货信息锁定。 */
    @Transactional
    public void updateReceiver(Long orderId, Long userId, String role, String name, String phone, String address) {
        Order order = orderMapper.findById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if ("customer".equals(role) && !userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("无权修改他人订单");
        }
        String current = normalizeStatus(order.getStatus());
        if (!"PENDING_PAY".equals(current) && !"PAID".equals(current)) {
            throw new IllegalArgumentException("订单已发货或已结束，收货信息不可修改");
        }
        if (name == null || name.isBlank()) throw new IllegalArgumentException("请填写收货人姓名");
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("请填写收货人电话");
        if (!phone.trim().matches("^1[3-9]\\d{9}$")) throw new IllegalArgumentException("收货人电话必须为 11 位手机号");
        if (address == null || address.isBlank()) throw new IllegalArgumentException("请填写收货地址");
        orderMapper.updateReceiver(orderId, name.trim(), phone.trim(), address.trim());
    }

    private String normalizeStatus(String status) {
        if (status == null) return "PENDING_PAY";
        return switch (status.toUpperCase()) {
            case "PAID" -> "PAID";
            case "SHIPPED" -> "SHIPPED";
            case "DELIVERED", "COMPLETED" -> "COMPLETED";
            case "CANCELLED" -> "CANCELLED";
            default -> "PENDING_PAY";
        };
    }

    private Map<String, Set<String>> allowedTransitions() {
        return Map.of("PENDING_PAY", Set.of("PAID", "CANCELLED"), "PAID", Set.of("SHIPPED", "CANCELLED"),
                "SHIPPED", Set.of("COMPLETED"), "COMPLETED", Set.of(), "CANCELLED", Set.of());
    }

    private String nextOrderNo() {
        return "CO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    /** 订单状态推进后投递站内通知（旁路，失败不影响主流程）。 */
    private void notifyOrderStatus(Order order, String target) {
        String no = order.getOrderNo();
        switch (target) {
            case "PAID" -> notificationService.create(order.getUserId(), "ORDER", "订单支付成功",
                    "您的订单 " + no + " 已支付成功，我们将尽快为您安排发货。", "ORDER", order.getId());
            case "COMPLETED" -> notificationService.create(order.getUserId(), "ORDER", "订单已完成",
                    "您的订单 " + no + " 已确认收货，感谢您的惠顾！", "ORDER", order.getId());
            case "CANCELLED" -> notificationService.create(order.getUserId(), "ORDER", "订单已取消",
                    "您的订单 " + no + " 已取消。", "ORDER", order.getId());
            default -> { /* 其余状态不通知 */ }
        }
    }

    /** 兼容旧的直接支付入口：为订单补记一条「支付成功」流水，使其与网关支付一致、可对账、可原路退款。 */
    private void recordSuccessFlow(Order order) {
        Payment p = new Payment();
        p.setPaymentNo(PaymentService.genNo("PAY"));
        p.setBizType("ORDER");
        p.setBizId(order.getId());
        p.setUserId(order.getUserId());
        p.setAmount(order.getTotalAmount());
        p.setChannel(normalizeChannel(order.getPayType()));
        p.setTransactionId(PaymentService.genNo("TXN"));
        paymentMapper.insertSuccess(p);
    }

    /** 取消已付款订单时，将其最近一笔成功流水回写为「已退款」，保证资金可追溯。 */
    private void refundSuccessFlow(Order order) {
        Payment p = paymentMapper.findLatestSuccessByBiz("ORDER", order.getId());
        if (p != null) {
            paymentMapper.markRefunded(p.getId(), PaymentService.genNo("RFD"), p.getAmount());
        }
    }

    private String normalizeChannel(String payType) {
        if (payType == null) return "alipay";
        return switch (payType.toLowerCase()) {
            case "wechat", "alipay", "bank" -> payType.toLowerCase();
            default -> "alipay";
        };
    }
}

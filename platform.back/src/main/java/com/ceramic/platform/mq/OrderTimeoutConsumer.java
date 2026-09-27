package com.ceramic.platform.mq;

import com.ceramic.platform.config.RabbitMQConfig;
import com.ceramic.platform.entity.Order;
import com.ceramic.platform.mapper.OrderMapper;
import com.ceramic.platform.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时消费者：消费从延迟队列死信转投过来的消息，将仍处于待支付的超时订单自动取消。
 *
 * 幂等与并发安全：
 * - 已支付/已取消/不存在的订单一律忽略（重复消息、先付后超时都无害）；
 * - 复用 OrderService.updateStatus 的 CAS 闸门（updateStatusGuarded 以 PENDING_PAY 为前置条件），
 *   与用户支付并发时只有一方成功，不会出现「已取消还扣款」或「重复扣库存」；
 * - 待支付订单未占用库存（支付时才扣减），取消无需回补库存。
 */
@Component
public class OrderTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutConsumer.class);

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    public OrderTimeoutConsumer(OrderMapper orderMapper, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    @RabbitListener(queues = RabbitMQConfig.ORDER_TIMEOUT_QUEUE)
    public void onTimeout(String orderIdStr) {
        Long orderId;
        try {
            orderId = Long.parseLong(orderIdStr == null ? "" : orderIdStr.trim());
        } catch (NumberFormatException e) {
            log.warn("收到非法的订单超时消息体：{}", orderIdStr);
            return;
        }
        Order order = orderMapper.findById(orderId);
        if (order == null) {
            log.info("超时消息对应订单不存在，忽略：orderId={}", orderId);
            return;
        }
        String status = order.getStatus() == null ? "" : order.getStatus().toUpperCase();
        if (!"PENDING_PAY".equals(status)) {
            // 已支付/已取消等：先于超时完成的合法流转，幂等忽略
            log.info("订单 {} 当前状态 {}，无需超时取消，忽略", orderId, status);
            return;
        }
        // 复用统一状态机：CAS 失败（并发已被改）返回 false，直接忽略
        boolean cancelled = orderService.updateStatus(orderId, "CANCELLED");
        if (cancelled) {
            log.info("订单 {} 超时未支付，已自动取消", orderId);
        } else {
            log.info("订单 {} 超时取消时状态已被并发修改，忽略", orderId);
        }
    }
}

package com.ceramic.platform.mq;

import com.ceramic.platform.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订单超时延迟消息生产者：下单成功后投递一条 TTL 消息到延迟队列。
 * 发送失败仅记日志，不影响下单主流程（定时任务兜底扫描仍会取消超时订单）。
 */
@Component
public class OrderTimeoutPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final int timeoutMinutes;

    public OrderTimeoutPublisher(RabbitTemplate rabbitTemplate,
                                 @Value("${order.pay-timeout-minutes:30}") int timeoutMinutes) {
        this.rabbitTemplate = rabbitTemplate;
        this.timeoutMinutes = timeoutMinutes;
    }

    public void publish(Long orderId, String orderNo) {
        try {
            // 消息体用字符串，避免跨端反序列化兼容问题；orderId 为唯一业务标识
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_DELAY_QUEUE, String.valueOf(orderId), message -> {
                // 消息级 TTL：毫秒（队列未设统一 TTL，改配置无需重建队列）
                message.getMessageProperties().setExpiration(String.valueOf(timeoutMinutes * 60_000L));
                message.getMessageProperties().setHeader("orderNo", orderNo == null ? "" : orderNo);
                return message;
            });
            log.info("已投递订单超时延迟消息：orderId={}，TTL={} 分钟", orderId, timeoutMinutes);
        } catch (Exception e) {
            // 下单不能因为 MQ 抖动而失败；超时兜底由定时任务保证
            log.error("投递订单超时延迟消息失败（定时任务将兜底取消）：orderId={}", orderId, e);
        }
    }
}

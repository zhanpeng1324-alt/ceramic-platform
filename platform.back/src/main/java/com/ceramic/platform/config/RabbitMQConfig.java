package com.ceramic.platform.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 订单超时取消拓扑（TTL + 死信队列实现延迟，原生支持、无需插件）。
 *
 * 链路：下单成功 → order.delay.queue（TTL=pay-timeout-minutes，不设消费者）
 *      → TTL 到期自动转投死信交换机 order.timeout.exchange
 *      → order.timeout.queue → OrderTimeoutConsumer 消费并 CAS 取消。
 *
 * 统一队列 TTL，不存在「队头阻塞」问题（长 TTL 消息堵住短 TTL 的缺陷仅在混合 TTL 时出现）。
 * 消费侧幂等：收到消息后校验订单仍为 PENDING_PAY 才取消，已支付/已取消一律忽略。
 */
@Configuration
public class RabbitMQConfig {

    /** 延迟队列：消息在此等待 TTL 到期（无消费者）。 */
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    /** 死信转投后真正被消费的超时队列。 */
    public static final String ORDER_TIMEOUT_QUEUE = "order.timeout.queue";
    public static final String ORDER_TIMEOUT_EXCHANGE = "order.timeout.exchange";
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "order.timeout";

    @Bean
    public DirectExchange orderTimeoutExchange() {
        return new DirectExchange(ORDER_TIMEOUT_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                // TTL 到期后转投死信交换机
                .deadLetterExchange(ORDER_TIMEOUT_EXCHANGE)
                .deadLetterRoutingKey(ORDER_TIMEOUT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(ORDER_TIMEOUT_QUEUE).build();
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue()).to(orderTimeoutExchange()).with(ORDER_TIMEOUT_ROUTING_KEY);
    }

    // ==================== 秒杀链路 ====================

    /** 抢购成功 → 异步落库队列（削峰：Redis 扣减成功后经此队列排队写 MySQL） */
    public static final String SECKILL_CREATE_QUEUE = "seckill.create.queue";
    public static final String SECKILL_CREATE_EXCHANGE = "seckill.create.exchange";
    public static final String SECKILL_CREATE_ROUTING_KEY = "seckill.create";

    /** 秒杀单超时取消：延迟队列（TTL）→ 死信转投 → 消费回补库存 */
    public static final String SECKILL_DELAY_QUEUE = "seckill.delay.queue";
    public static final String SECKILL_TIMEOUT_QUEUE = "seckill.timeout.queue";
    public static final String SECKILL_TIMEOUT_EXCHANGE = "seckill.timeout.exchange";
    public static final String SECKILL_TIMEOUT_ROUTING_KEY = "seckill.timeout";

    @Bean
    public DirectExchange seckillCreateExchange() {
        return new DirectExchange(SECKILL_CREATE_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillCreateQueue() {
        return QueueBuilder.durable(SECKILL_CREATE_QUEUE).build();
    }

    @Bean
    public Binding seckillCreateBinding() {
        return BindingBuilder.bind(seckillCreateQueue()).to(seckillCreateExchange()).with(SECKILL_CREATE_ROUTING_KEY);
    }

    @Bean
    public DirectExchange seckillTimeoutExchange() {
        return new DirectExchange(SECKILL_TIMEOUT_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillDelayQueue() {
        return QueueBuilder.durable(SECKILL_DELAY_QUEUE)
                .deadLetterExchange(SECKILL_TIMEOUT_EXCHANGE)
                .deadLetterRoutingKey(SECKILL_TIMEOUT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue seckillTimeoutQueue() {
        return QueueBuilder.durable(SECKILL_TIMEOUT_QUEUE).build();
    }

    @Bean
    public Binding seckillTimeoutBinding() {
        return BindingBuilder.bind(seckillTimeoutQueue()).to(seckillTimeoutExchange()).with(SECKILL_TIMEOUT_ROUTING_KEY);
    }
}

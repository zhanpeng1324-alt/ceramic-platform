package com.ceramic.platform.mq;

import com.ceramic.platform.config.RabbitMQConfig;
import com.ceramic.platform.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单异步落库消费者。
 * 抢购洪峰在 Redis Lua 扣减处被挡下，仅成功请求进入本队列排队写 MySQL——数据库按自身节奏消费，实现削峰。
 * 失败处理：落库异常时 SeckillService 已回补 Redis 库存并重抛，消息 nack 后不无限重试（defaultRequeueRejected 默认 false 进死信丢弃），
 * 期间用户可重新抢购，库存不丢失。
 */
@Component
public class SeckillOrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillOrderConsumer.class);

    private final SeckillService seckillService;

    public SeckillOrderConsumer(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    @RabbitListener(queues = RabbitMQConfig.SECKILL_CREATE_QUEUE)
    public void onCreate(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        log.info("秒杀订单落库：{}", payload);
        seckillService.persistOrder(payload);
    }
}

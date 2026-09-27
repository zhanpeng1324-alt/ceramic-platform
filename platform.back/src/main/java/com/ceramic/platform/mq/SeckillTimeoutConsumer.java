package com.ceramic.platform.mq;

import com.ceramic.platform.config.RabbitMQConfig;
import com.ceramic.platform.entity.SeckillActivity;
import com.ceramic.platform.entity.SeckillOrder;
import com.ceramic.platform.mapper.SeckillActivityMapper;
import com.ceramic.platform.mapper.SeckillOrderMapper;
import com.ceramic.platform.service.SeckillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 秒杀单超时消费者：30 分钟未支付的秒杀单自动取消并逐级回补库存（DB + Redis + 抢购资格）。
 *
 * 幂等与并发安全：
 * - CAS（PENDING → CANCELLED）与用户支付互斥，先到先得，只有一方成功；
 * - 已支付/已取消/不存在的订单一律忽略，重复消息无害；
 * - CAS 成功才回补，杜绝「已支付还被回补」的双花。
 */
@Component
public class SeckillTimeoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(SeckillTimeoutConsumer.class);

    private final SeckillOrderMapper orderMapper;
    private final SeckillActivityMapper activityMapper;
    private final SeckillService seckillService;

    public SeckillTimeoutConsumer(SeckillOrderMapper orderMapper, SeckillActivityMapper activityMapper,
                                  SeckillService seckillService) {
        this.orderMapper = orderMapper;
        this.activityMapper = activityMapper;
        this.seckillService = seckillService;
    }

    @RabbitListener(queues = RabbitMQConfig.SECKILL_TIMEOUT_QUEUE)
    public void onTimeout(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return;
        }
        SeckillOrder o = orderMapper.findByOrderNo(orderNo.trim());
        if (o == null) {
            // 订单尚未落库（创建队列积压中）或不存在：由对账兜底，这里忽略
            log.info("秒杀超时消息对应订单不存在，忽略：{}", orderNo);
            return;
        }
        int rows = orderMapper.updateStatusGuarded(o.getId(), "CANCELLED", "PENDING");
        if (rows == 0) {
            log.info("秒杀单 {} 已支付或已取消，超时消息忽略", orderNo);
            return;
        }
        // 活动已下线：订单照常关闭，但不再回补——Redis 库存/资格键已随下线删除，
        // 此时 increment 会重建出无 TTL 的僵尸键，DB 库存对已作废活动也失去意义
        SeckillActivity act = activityMapper.findById(o.getActivityId());
        if (act != null && "CANCELLED".equals(act.getStatus())) {
            log.info("活动已下线，秒杀单 {} 超时关闭且不回补库存", orderNo);
            return;
        }
        // 回补：DB 库存 + Redis 库存 + 抢购资格
        activityMapper.restock(o.getActivityId(), o.getQuantity());
        seckillService.restockRedis(o.getActivityId(), o.getUserId());
        log.info("秒杀单 {} 超时未支付，已取消并回补库存", orderNo);
    }
}

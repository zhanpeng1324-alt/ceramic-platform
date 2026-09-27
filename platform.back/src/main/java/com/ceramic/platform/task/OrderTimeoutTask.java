package com.ceramic.platform.task;

import com.ceramic.platform.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时取消超时未支付订单。
 *
 * 仅将 PENDING_PAY 且超过 order.pay-timeout-minutes 分钟仍未支付的订单置为 CANCELLED。
 * 待支付订单不占用库存（库存在支付时才扣减），因此取消时无需回补库存。
 * 该任务只会命中「未支付且超时」的订单，对已付款/已发货/已完成订单没有任何影响。
 */
@Component
public class OrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);

    private final OrderMapper orderMapper;
    private final int timeoutMinutes;

    public OrderTimeoutTask(OrderMapper orderMapper,
                            @Value("${order.pay-timeout-minutes:30}") int timeoutMinutes) {
        this.orderMapper = orderMapper;
        this.timeoutMinutes = timeoutMinutes;
    }

    /** 每隔一段时间扫描一次（启动后延迟 1 分钟再首次执行，避免启动瞬间抖动）。 */
    @Scheduled(fixedDelayString = "${order.pay-timeout-check-ms:300000}", initialDelay = 60000)
    public void cancelTimeoutOrders() {
        int cancelled = orderMapper.cancelExpiredPending(timeoutMinutes);
        if (cancelled > 0) {
            log.info("自动取消超时未支付订单 {} 笔（超时阈值 {} 分钟）", cancelled, timeoutMinutes);
        }
    }
}

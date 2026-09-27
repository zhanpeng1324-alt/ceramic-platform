package com.ceramic.platform.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 秒杀订单。抢购成功先经 Redis 原子扣减，订单本体由 MQ 消费者异步落库（削峰）。
 * 状态机：PENDING → PAID（支付）/ CANCELLED（超时未付，回补库存）/ REFUNDED（支付后退款，不回补库存与资格）。
 * 唯一索引 uk_user_activity 保证一人一单的数据库级兜底。
 */
@Data
public class SeckillOrder {
    private Long id;
    private Long activityId;
    private Long userId;
    private Long productId;
    private Integer quantity;
    private BigDecimal seckillPrice;
    private BigDecimal payAmount;
    /** PENDING / PAID / CANCELLED / REFUNDED */
    private String status;
    private String orderNo;
    /** 秒杀单转正式订单后回填的正式订单号（售后退款联动定位用） */
    private String convertOrderNo;
    private String createdAt;

    /** 非表字段（我的秒杀单列表展示用） */
    private String productName;
    private String productImage;
}

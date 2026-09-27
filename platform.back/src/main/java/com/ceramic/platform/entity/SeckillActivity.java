package com.ceramic.platform.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 秒杀活动。展示状态（未开始/进行中/已结束）由服务层按时间实时计算，
 * 库存扣减以 Redis Lua 原子操作为准，DB available_stock 为对账与回补依据。
 */
@Data
public class SeckillActivity {
    private Long id;
    private Long productId;
    /** 秒杀价 */
    private BigDecimal seckillPrice;
    private Integer totalStock;
    private Integer availableStock;
    private String startTime;
    private String endTime;
    /** 创建时依据时间窗的初始标记：PENDING / ACTIVE（实际展示状态按当前时间计算） */
    private String status;
    private String createdAt;

    /** 以下为关联商品的非表字段（列表/详情展示用） */
    private String productName;
    private String productImage;
    private BigDecimal originalPrice;
}

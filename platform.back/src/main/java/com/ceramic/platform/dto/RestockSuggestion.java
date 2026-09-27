package com.ceramic.platform.dto;

import lombok.Data;

/**
 * 备货建议：对每个需要关注的在售商品，结合近 30 天真实销量给出补货提示。
 * 由 RestockService 计算，仅管理员可见。
 */
@Data
public class RestockSuggestion {

    /** 商品 ID */
    private Long productId;

    /** 商品名称 */
    private String name;

    /** 商品图片 */
    private String imageUrl;

    /** 当前库存 */
    private Integer stock;

    /** 近 30 天销量 */
    private Integer sold30;

    /** 建议补货数量（补到约 30 天用量） */
    private Integer suggestedRestock;

    /** 按当前日均销量预计还能卖的天数；无销量时为 null（表示暂不会售罄） */
    private Integer daysLeft;

    /** 紧急程度：OUT 缺货 / URGENT 紧急 / WARNING 偏低 */
    private String level;

    /** 触发原因说明，便于管理员理解 */
    private String reason;
}

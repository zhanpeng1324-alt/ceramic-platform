package com.ceramic.platform.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Customization {
    private Long id;
    private Long userId;
    private Long productId;
    private String designSpecifications;
    private String designImageUrl;
    private String finishedProductUrl;
    private BigDecimal price;
    private BigDecimal quotedPrice;
    private BigDecimal depositAmount;
    private Boolean depositPaid;
    /** 尾款金额 = 报价 − 定金，报价时算出 */
    private BigDecimal finalAmount;
    /** 尾款是否已支付 */
    private Boolean finalPaid;
    /** 尾款支付时间 */
    private String finalPayTime;
    private String shape;
    private String glazeColor;
    private String pattern;
    private String inscription;
    private String size;
    private Integer quantity;
    private BigDecimal budget;
    private String contactName;
    private String contactPhone;
    /** 收货地址：定制成品完成后据此发货 */
    private String shippingAddress;
    private String requirement;
    private String status;
    private String notes;
    private String timelineNote;
    /** 预计完成日期 yyyy-MM-dd */
    private String expectedCompleteDate;
    private String createdAt;
    private String updatedAt;
}

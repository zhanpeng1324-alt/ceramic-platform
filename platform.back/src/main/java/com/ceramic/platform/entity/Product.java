package com.ceramic.platform.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Product {
    private Long id;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String imageUrl;
    /** 商品图册：JSON 数组字符串（多张图片 URL），首图为封面。为空时前端回退 imageUrl。 */
    private String images;
    private String glazeColor;
    private String material;
    private String size;
    private Boolean customizable;
    private String status;
    private String createdAt;
    private String updatedAt;
    /** 已售件数：非表字段，列表接口按有效订单实时聚合填充，不参与持久化 */
    private Integer sales;
}

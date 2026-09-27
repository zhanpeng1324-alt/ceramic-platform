package com.ceramic.platform.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItem {
    private Long id;
    @JsonProperty("userId")
    private Long userId;
    @JsonProperty("productId")
    private Long productId;
    @JsonProperty("quantity")
    private Integer quantity;
    @JsonProperty("selectedOptions")
    private String selectedOptions;
    private String createdAt;
    private String updatedAt;
    @JsonProperty("productName")
    private String productName;
    @JsonProperty("imageUrl")
    private String imageUrl;
    @JsonProperty("price")
    private BigDecimal price;
    /** 商品当前上下架状态（active/inactive），仅查询时联表带出 */
    @JsonProperty("productStatus")
    private String productStatus;
    /** 商品当前库存，仅查询时联表带出 */
    @JsonProperty("stock")
    private Integer stock;
}

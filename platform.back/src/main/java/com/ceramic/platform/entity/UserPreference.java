package com.ceramic.platform.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserPreference {
    private Long id;
    private Long userId;
    private Long categoryId;
    private BigDecimal preferredPriceMin;
    private BigDecimal preferredPriceMax;
    private String preferredMaterial;
    private String preferredGlazeColor;
    private String createdAt;
    private String updatedAt;
}
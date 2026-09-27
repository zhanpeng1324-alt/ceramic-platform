package com.ceramic.platform.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RecommendationRule {
    private Long id;
    private String name;
    private String type;
    private String criteria;
    private BigDecimal weight;
    private Integer isActive;
    private String createdAt;
    private String updatedAt;
}
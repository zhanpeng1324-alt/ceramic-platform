package com.ceramic.platform.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Recommendation {
    private Long id;
    private Long userId;
    private Long productId;
    private String reason;
    private BigDecimal score;
    private String createdAt;
    private String updatedAt;
}
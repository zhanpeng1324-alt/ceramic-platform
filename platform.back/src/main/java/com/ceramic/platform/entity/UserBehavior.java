package com.ceramic.platform.entity;

import lombok.Data;

@Data
public class UserBehavior {
    private Long id;
    private Long userId;
    private Long productId;
    private String action;
    private String timestamp;
    private Integer sessionDuration;
    private String referrer;
}
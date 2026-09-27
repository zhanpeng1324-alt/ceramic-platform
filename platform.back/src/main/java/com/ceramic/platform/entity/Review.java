package com.ceramic.platform.entity;

import lombok.Data;

@Data
public class Review {
    private Long id;
    private Long userId;
    private Long productId;
    private Integer rating;
    private String title;
    private String content;
    private String images;
    private Integer helpfulCount;
    private Integer reportedCount;
    private String status;
    private String reply;
    private String replyTime;
    private String createdAt;
    private String updatedAt;
    private String username;
    private String productName;
}

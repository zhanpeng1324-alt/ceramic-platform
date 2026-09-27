package com.ceramic.platform.entity;

import lombok.Data;

@Data
public class SupportTicket {
    private Long id;
    private Long userId;
    private Long orderId;
    private String type;
    private String title;
    private String content;
    private String contactName;
    private String contactPhone;
    private String priority;
    private String status;
    private String reply;
    private String createdAt;
    private String updatedAt;
    private String customerName;
}

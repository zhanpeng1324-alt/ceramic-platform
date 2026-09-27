package com.ceramic.platform.entity;

import lombok.Data;

@Data
public class ChatConversation {
    private Long id;
    private Long customerId;
    private Long supportAgentId;
    private String status;
    private String subject;
    private String priority;
    private Integer unreadCount;
    private String lastMessage;
    private String lastMessageTime;
    private String createdAt;
    private String updatedAt;
    private String customerName;
    private Boolean transferredToHuman;
    private String aiSummary;
}
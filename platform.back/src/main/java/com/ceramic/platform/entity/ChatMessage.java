package com.ceramic.platform.entity;

import lombok.Data;

@Data
public class ChatMessage {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderRole;
    private String message;
    private String type;
    private String timestamp;
    private Boolean readBySupport;
    private Boolean readByCustomer;
}
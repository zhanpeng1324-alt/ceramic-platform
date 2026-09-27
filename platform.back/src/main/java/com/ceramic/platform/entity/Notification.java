package com.ceramic.platform.entity;

import lombok.Data;

/**
 * 站内通知。业务状态推进后 append-only 写入，不影响原业务流转。
 */
@Data
public class Notification {
    private Long id;
    private Long userId;
    /** ORDER / CUSTOMIZATION / RETURN / SYSTEM */
    private String type;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private Boolean isRead;
    private String createdAt;
    private String readAt;
}

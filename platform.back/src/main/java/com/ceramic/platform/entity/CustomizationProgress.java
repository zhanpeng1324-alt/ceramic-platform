package com.ceramic.platform.entity;

import lombok.Data;

@Data
public class CustomizationProgress {
    private Long id;
    private Long customizationId;
    private String stage;
    private String description;
    private String imageUrl;
    /** 操作人ID */
    private Long operatorId;
    /** 操作人角色 */
    private String operatorRole;
    /** 变更前状态 */
    private String fromStatus;
    /** 变更后状态 */
    private String toStatus;
    private String createdAt;
}

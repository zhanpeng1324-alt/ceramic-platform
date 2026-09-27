package com.ceramic.platform.entity;

import lombok.Data;

@Data
public class AuditLog {
    private Long id;
    private Long operatorId;
    private String operatorRole;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String detail;
    private String ipAddress;
    private String createdAt;
}

package com.ceramic.platform.entity;

import lombok.Data;

@Data
public class ReturnRequestLog {
    private Long id;
    private Long returnRequestId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorRole;
    private String note;
    private String createdAt;
}

package com.ceramic.platform.entity;

import lombok.Data;

@Data
public class UserAddress {
    private Long id;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private Boolean isDefault;
    private String createdAt;
    private String updatedAt;
}

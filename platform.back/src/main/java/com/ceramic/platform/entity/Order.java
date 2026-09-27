package com.ceramic.platform.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Order {
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private String status;
    private String payType;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private String createdAt;
    private String payTime;
    private String shippingCompany;
    private String trackingNo;
    private String shipTime;
    private String updatedAt;
}

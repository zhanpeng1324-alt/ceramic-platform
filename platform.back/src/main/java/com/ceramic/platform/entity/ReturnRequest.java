package com.ceramic.platform.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReturnRequest {
    private Integer id;
    private Integer orderId;
    private Integer orderItemId;
    private Integer userId;
    private Integer productId;
    private String productName;
    private String imageUrl;
    private BigDecimal unitPrice;
    private Integer quantity;
    private String returnType;
    private String reason;
    private String description;
    private String status;
    private String rejectReason;
    private BigDecimal refundAmount;
    private String trackingNo;
    /** 凭证图片URL，JSON数组字符串 */
    private String evidenceImages;
    /** 退货地址（管理员/客服填写） */
    private String returnAddress;
    private String createdAt;
    private String updatedAt;
}
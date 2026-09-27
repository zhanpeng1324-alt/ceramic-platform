package com.ceramic.platform.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟支付流水。
 * 一条流水对应一次「发起支付 → 网关回调」的完整过程，是替换原「直接翻状态」演示支付的载体。
 */
@Data
public class Payment {
    private Long id;
    private String paymentNo;
    /** ORDER / CUSTOM_DEPOSIT / CUSTOM_BALANCE */
    private String bizType;
    private Long bizId;
    private Long userId;
    private BigDecimal amount;
    /** 模拟支付渠道：alipay / wechat / bank */
    private String channel;
    /** PENDING / SUCCESS / FAILED / REFUNDED */
    private String status;
    private String transactionId;
    private String refundNo;
    private BigDecimal refundAmount;
    private String createdAt;
    private String paidAt;
    private String refundedAt;
}

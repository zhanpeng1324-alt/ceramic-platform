package com.ceramic.platform.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateOrderRequest {
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private String payType;
    /** 勾选结算：要结算的购物车项 id；为空表示整购物车下单（兼容旧入口） */
    private List<Long> cartItemIds;
}

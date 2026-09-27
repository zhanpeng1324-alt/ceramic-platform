package com.ceramic.platform.entity;

import lombok.Data;

/** 店铺设置（单商户，全表仅一行）。发货寄件方信息，同时作为默认退货地址。 */
@Data
public class ShopSettings {
    private Long id;
    private String shopName;
    private String contactName;
    private String contactPhone;
    private String address;
    private String updatedAt;
}

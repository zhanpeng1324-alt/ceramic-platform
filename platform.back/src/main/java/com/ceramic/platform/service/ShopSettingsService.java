package com.ceramic.platform.service;

import com.ceramic.platform.entity.ShopSettings;
import com.ceramic.platform.mapper.ShopSettingsMapper;
import org.springframework.stereotype.Service;

/** 店铺设置服务：单商户全局一行，缺省自动补建。 */
@Service
public class ShopSettingsService {
    private final ShopSettingsMapper shopSettingsMapper;

    public ShopSettingsService(ShopSettingsMapper shopSettingsMapper) {
        this.shopSettingsMapper = shopSettingsMapper;
    }

    /** 读取店铺设置；表为空时返回空对象（读操作不产生写副作用）。 */
    public ShopSettings get() {
        ShopSettings s = shopSettingsMapper.find();
        return s != null ? s : new ShopSettings();
    }

    /** 保存店铺设置（仅 admin 调用）。表为空时插入，否则更新既有行。 */
    public ShopSettings save(ShopSettings input) {
        if (input.getContactName() == null || input.getContactName().isBlank())
            throw new IllegalArgumentException("请填写联系人姓名");
        if (input.getContactPhone() == null || input.getContactPhone().isBlank())
            throw new IllegalArgumentException("请填写联系电话");
        if (!input.getContactPhone().trim().matches("^1[3-9]\\d{9}$"))
            throw new IllegalArgumentException("联系电话必须为 11 位手机号");
        if (input.getAddress() == null || input.getAddress().isBlank())
            throw new IllegalArgumentException("请填写店铺地址");
        ShopSettings existing = shopSettingsMapper.find();
        if (existing == null) {
            shopSettingsMapper.insert(input);
        } else {
            input.setId(existing.getId());
            shopSettingsMapper.update(input);
        }
        return shopSettingsMapper.find();
    }
}

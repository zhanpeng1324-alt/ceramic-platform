package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.ShopSettings;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.AuditLogService;
import com.ceramic.platform.service.ShopSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 店铺设置：GET 供已登录用户读取寄件方/退货地址；PUT 仅 admin 可改。 */
@RestController
@RequestMapping("/api/shop-settings")
public class ShopSettingsController {
    private final ShopSettingsService shopSettingsService;
    private final AuditLogService auditLogService;

    public ShopSettingsController(ShopSettingsService shopSettingsService, AuditLogService auditLogService) {
        this.shopSettingsService = shopSettingsService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Result<ShopSettings> get() {
        return Result.success(shopSettingsService.get());
    }

    @PutMapping
    public Result<ShopSettings> update(@RequestBody ShopSettings body, HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null || !"admin".equals(user.getRole())) return Result.error(403, "仅管理员可修改店铺设置");
        ShopSettings saved = shopSettingsService.save(body);
        auditLogService.log(user.getId(), user.getRole(), "UPDATE", "SHOP_SETTINGS",
                saved.getId(), "修改店铺设置", request.getRemoteAddr());
        return Result.success(saved);
    }
}

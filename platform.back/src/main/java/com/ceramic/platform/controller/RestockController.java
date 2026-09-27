package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.dto.RestockSuggestion;
import com.ceramic.platform.service.RestockService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 备货建议接口。
 * 路径 /api/admin/** 由 AuthInterceptor 限制仅 admin 角色可访问，此处再做一次防御性校验。
 */
@RestController
@RequestMapping("/api/admin/restock")
public class RestockController {

    private final RestockService restockService;

    public RestockController(RestockService restockService) {
        this.restockService = restockService;
    }

    @GetMapping
    public Result<List<RestockSuggestion>> suggestions(HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if (!"admin".equals(role)) {
            return Result.error(403, "无权访问");
        }
        return Result.success(restockService.suggest());
    }
}

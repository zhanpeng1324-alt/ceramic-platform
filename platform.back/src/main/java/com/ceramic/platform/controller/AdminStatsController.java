package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.dto.AdminStatsDTO;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.AdminStatsService;
import com.ceramic.platform.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员数据看板统计接口。
 * 路径 /api/admin/** 由 AuthInterceptor 限制仅 admin 角色可访问，
 * 此处再做一次防御性校验，确保统计接口仅管理员可用。
 */
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;
    private final AuditLogService auditLogService;

    public AdminStatsController(AdminStatsService adminStatsService, AuditLogService auditLogService) {
        this.adminStatsService = adminStatsService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Result<AdminStatsDTO> stats(HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if (!"admin".equals(role)) {
            return Result.error(403, "无权访问");
        }
        User currentUser = (User) request.getAttribute("currentUser");
        auditLogService.log(currentUser.getId(), role, "VIEW_STATS",
                "DASHBOARD", null, "查看管理员数据看板", request.getRemoteAddr());
        return Result.success(adminStatsService.getStats());
    }
}

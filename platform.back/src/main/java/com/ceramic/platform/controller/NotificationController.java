package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.Notification;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内通知中心接口（当前登录用户维度）。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Result<List<Notification>> list(HttpServletRequest request) {
        User currentUser = currentUser(request);
        return Result.success(notificationService.listByUser(currentUser.getId()));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Integer>> unreadCount(HttpServletRequest request) {
        User currentUser = currentUser(request);
        return Result.success(Map.of("count", notificationService.unreadCount(currentUser.getId())));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = currentUser(request);
        notificationService.markRead(id, currentUser.getId());
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead(HttpServletRequest request) {
        User currentUser = currentUser(request);
        notificationService.markAllRead(currentUser.getId());
        return Result.success();
    }

    private User currentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }
}

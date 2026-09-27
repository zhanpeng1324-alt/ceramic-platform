package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.SupportTicket;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.SupportTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support-tickets")
public class SupportTicketController {
    private final SupportTicketService supportTicketService;

    public SupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @GetMapping
    public Result<List<SupportTicket>> list(@RequestParam(required = false) Long userId, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        if (!"admin".equals(role) && !"service".equals(role)) {
            userId = currentUser.getId();
        }
        return Result.success(supportTicketService.list(userId));
    }

    @PostMapping
    public Result<SupportTicket> create(@RequestBody SupportTicket ticket, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            ticket.setUserId(currentUser.getId());
            ticket.setContactName(currentUser.getNickname());
            ticket.setContactPhone(currentUser.getPhone());
        }
        return Result.success(supportTicketService.create(ticket));
    }

    @PatchMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody SupportTicket ticket, HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            return Result.error(403, "顾客无权回复工单");
        }
        supportTicketService.reply(id, ticket);
        return Result.success();
    }

    @PatchMapping("/{id}/priority")
    public Result<Void> updatePriority(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            return Result.error(403, "顾客无权修改优先级");
        }
        supportTicketService.updatePriority(id, body.get("priority"));
        return Result.success();
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            return Result.error(403, "顾客无权修改工单状态");
        }
        supportTicketService.updateStatus(id, body.get("status"));
        return Result.success();
    }
}

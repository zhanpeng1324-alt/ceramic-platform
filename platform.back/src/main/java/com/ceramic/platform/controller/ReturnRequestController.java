package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.Order;
import com.ceramic.platform.entity.ReturnRequest;
import com.ceramic.platform.entity.ReturnRequestLog;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.AuditLogService;
import com.ceramic.platform.service.OrderService;
import com.ceramic.platform.service.ReturnRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/return-requests")
public class ReturnRequestController {
    private final ReturnRequestService returnRequestService;
    private final AuditLogService auditLogService;
    private final OrderService orderService;

    public ReturnRequestController(ReturnRequestService returnRequestService, AuditLogService auditLogService, OrderService orderService) {
        this.returnRequestService = returnRequestService;
        this.auditLogService = auditLogService;
        this.orderService = orderService;
    }

    @GetMapping
    public Result<List<ReturnRequest>> list(@RequestParam(required = false) Integer userId, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            userId = currentUser.getId().intValue();
        }
        return Result.success(returnRequestService.list(userId));
    }

    @GetMapping("/{id}")
    public Result<ReturnRequest> detail(@PathVariable Integer id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        ReturnRequest req = returnRequestService.findById(id);
        if (req == null) return Result.error(404, "售后单不存在");
        if ("customer".equals(role) && !req.getUserId().equals(currentUser.getId().intValue())) {
            return Result.error(403, "无权查看他人售后单");
        }
        return Result.success(req);
    }

    @GetMapping("/{id}/logs")
    public Result<List<ReturnRequestLog>> logs(@PathVariable Integer id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        ReturnRequest req = returnRequestService.findById(id);
        if (req == null) return Result.error(404, "售后单不存在");
        if ("customer".equals(role) && !req.getUserId().equals(currentUser.getId().intValue())) {
            return Result.error(403, "无权查看他人售后单");
        }
        return Result.success(returnRequestService.getLogs(id.longValue()));
    }

    @GetMapping("/order/{orderId}")
    public Result<List<ReturnRequest>> listByOrder(@PathVariable Integer orderId, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            Order order = orderService.findById(orderId.longValue());
            if (order == null || !order.getUserId().equals(currentUser.getId())) {
                return Result.error(403, "无权查看该订单的售后记录");
            }
        }
        return Result.success(returnRequestService.findByOrderId(orderId));
    }

    @PostMapping
    public Result<ReturnRequest> create(@RequestBody ReturnRequest body, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        ReturnRequest created = returnRequestService.create(body, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "CREATE", "RETURN_REQUEST",
                created.getId() == null ? null : created.getId().longValue(),
                "提交售后申请，订单 " + created.getOrderId(), request.getRemoteAddr());
        return Result.success(created);
    }

    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Integer id, @RequestBody(required = false) Map<String, String> body,
                                HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        String returnAddress = body == null ? null : body.get("returnAddress");
        returnRequestService.approve(id, returnAddress, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "APPROVE",
                "RETURN_REQUEST", id.longValue(), "审核通过售后申请", request.getRemoteAddr());
        return Result.success();
    }

    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Integer id, @RequestBody(required = false) Map<String, String> body,
                               HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        String reason = body == null ? null : body.get("reason");
        returnRequestService.reject(id, reason, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "REJECT",
                "RETURN_REQUEST", id.longValue(), "驳回售后申请: " + reason, request.getRemoteAddr());
        return Result.success();
    }

    @PutMapping("/{id}/return-address")
    public Result<Void> fillReturnAddress(@PathVariable Integer id, @RequestBody Map<String, String> body,
                                          HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        String returnAddress = body.get("returnAddress");
        returnRequestService.fillReturnAddress(id, returnAddress, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "UPDATE",
                "RETURN_REQUEST", id.longValue(), "填写退货地址", request.getRemoteAddr());
        return Result.success();
    }

    @PutMapping("/{id}/ship-back")
    public Result<Void> shipBack(@PathVariable Integer id, @RequestBody Map<String, String> body,
                                 HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        String trackingNo = body.get("trackingNo");
        returnRequestService.shipBack(id, trackingNo, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "SHIP_BACK",
                "RETURN_REQUEST", id.longValue(), "买家寄回商品，快递单号: " + trackingNo, request.getRemoteAddr());
        return Result.success();
    }

    @PutMapping("/{id}/receive")
    public Result<Void> confirmReceive(@PathVariable Integer id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        returnRequestService.confirmReceive(id, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "RECEIVE",
                "RETURN_REQUEST", id.longValue(), "确认收到退货", request.getRemoteAddr());
        return Result.success();
    }

    @PutMapping("/{id}/refund")
    public Result<Void> confirmRefund(@PathVariable Integer id, @RequestBody(required = false) Map<String, Object> body,
                                      HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        BigDecimal refundAmount = null;
        if (body != null && body.get("refundAmount") != null) {
            refundAmount = new BigDecimal(String.valueOf(body.get("refundAmount")));
        }
        returnRequestService.confirmRefund(id, refundAmount, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "REFUND",
                "RETURN_REQUEST", id.longValue(), "确认退款", request.getRemoteAddr());
        return Result.success();
    }

    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Integer id, @RequestBody(required = false) Map<String, String> body,
                              HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        String reason = body == null ? null : body.get("reason");
        returnRequestService.close(id, reason, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "CLOSE",
                "RETURN_REQUEST", id.longValue(), "关闭售后", request.getRemoteAddr());
        return Result.success();
    }
}

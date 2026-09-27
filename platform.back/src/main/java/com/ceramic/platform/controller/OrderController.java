package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.dto.CreateOrderRequest;
import com.ceramic.platform.dto.LogisticsTrace;
import com.ceramic.platform.entity.Order;
import com.ceramic.platform.entity.OrderItem;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.AuditLogService;
import com.ceramic.platform.service.LogisticsService;
import com.ceramic.platform.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;
    private final AuditLogService auditLogService;
    private final LogisticsService logisticsService;

    public OrderController(OrderService orderService, AuditLogService auditLogService, LogisticsService logisticsService) {
        this.orderService = orderService;
        this.auditLogService = auditLogService;
        this.logisticsService = logisticsService;
    }

    @GetMapping
    public Result<List<Order>> list(@RequestParam(required = false) Long userId, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        if (!"admin".equals(role) && !"service".equals(role)) {
            userId = currentUser.getId();
        } else if (userId == null) {
            return Result.success(orderService.list(null));
        }
        return Result.success(orderService.list(userId));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            Order order = orderService.findById(id);
            if (order == null || !order.getUserId().equals(currentUser.getId())) {
                return Result.error(403, "无权查看该订单");
            }
        }
        return Result.success(orderService.detail(id));
    }

    @GetMapping("/{id}/items")
    public Result<List<OrderItem>> items(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            Order order = orderService.findById(id);
            if (order == null || !order.getUserId().equals(currentUser.getId())) {
                return Result.error(403, "无权查看该订单");
            }
        }
        return Result.success(orderService.findItems(id));
    }

    /** 订单物流轨迹（模拟物流网关生成；归属校验在 service 层）。 */
    @GetMapping("/{id}/logistics")
    public Result<List<LogisticsTrace>> logistics(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        return Result.success(logisticsService.trace(id, currentUser.getId(), role));
    }

    @PostMapping
    public Result<Order> create(@RequestBody CreateOrderRequest req, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        req.setUserId(currentUser.getId());
        Order order = orderService.createFromCart(req);
        auditLogService.log(currentUser.getId(), role, "CREATE", "ORDER", order.getId(),
                "创建订单 " + order.getOrderNo() + "，金额 ¥" + order.getTotalAmount(), request.getRemoteAddr());
        return Result.success(order);
    }

    /** Local/demo payment confirmation. Replace this endpoint with a verified payment-provider callback in production. */
    @PostMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = currentUser(request);
        orderService.pay(id, currentUser.getId());
        auditLogService.log(currentUser.getId(), currentUser.getRole(), "PAY", "ORDER", id, "本地演示支付确认", request.getRemoteAddr());
        return Result.success();
    }

    @PostMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            return Result.error(403, "顾客无权发货");
        }
        User currentUser = currentUser(request);
        String shippingCompany = body.get("shippingCompany");
        String trackingNo = body.get("trackingNo");
        orderService.ship(id, shippingCompany, trackingNo);
        auditLogService.log(currentUser.getId(), role, "SHIP", "ORDER", id,
                "发货: " + shippingCompany + " " + trackingNo, request.getRemoteAddr());
        return Result.success();
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            return Result.error(403, "顾客无权修改订单状态");
        }
        User currentUser = currentUser(request);
        String newStatus = body.get("status");
        orderService.updateStatus(id, newStatus);
        auditLogService.log(currentUser.getId(), role, "UPDATE_STATUS",
                "ORDER", id, "订单状态更新为: " + newStatus, request.getRemoteAddr());
        return Result.success();
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        orderService.cancelOrder(id, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "CANCEL",
                "ORDER", id, "取消订单", request.getRemoteAddr());
        return Result.success();
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirmReceipt(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        orderService.confirmReceipt(id, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "CONFIRM_RECEIPT",
                "ORDER", id, "确认收货", request.getRemoteAddr());
        return Result.success();
    }

    /** 顾客/客服修改订单收货信息（仅未发货订单可改，归属与状态校验在 service 层）。 */
    @PutMapping("/{id}/address")
    public Result<Void> updateAddress(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        orderService.updateReceiver(id, currentUser.getId(), role,
                body.get("receiverName"), body.get("receiverPhone"), body.get("receiverAddress"));
        auditLogService.log(currentUser.getId(), role, "UPDATE_ADDRESS", "ORDER", id,
                "修改收货信息", request.getRemoteAddr());
        return Result.success();
    }

    private User currentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }
}

package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.Payment;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.AuditLogService;
import com.ceramic.platform.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 模拟支付网关接口。
 * 顾客：发起支付、触发回调、查询自己的流水；管理员/客服：退款。
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final AuditLogService auditLogService;

    public PaymentController(PaymentService paymentService, AuditLogService auditLogService) {
        this.paymentService = paymentService;
        this.auditLogService = auditLogService;
    }

    /** 发起支付，返回 PENDING 流水（含 paymentNo、应付金额）。 */
    @PostMapping
    public Result<Payment> initiate(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        String bizType = body.get("bizType") == null ? null : String.valueOf(body.get("bizType"));
        Long bizId = body.get("bizId") == null ? null : Long.valueOf(String.valueOf(body.get("bizId")));
        String channel = body.get("channel") == null ? null : String.valueOf(body.get("channel"));
        Payment p = paymentService.initiate(bizType, bizId, channel, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "PAY_INITIATE", "PAYMENT", p.getId(),
                "发起支付 " + bizType + "#" + bizId + " ¥" + p.getAmount(), request.getRemoteAddr());
        return Result.success(p);
    }

    /** 模拟收银台「确认支付」：顾客在网关完成付款，服务端确认入账（唯一对顾客开放的支付完成入口）。 */
    @PostMapping("/{paymentNo}/confirm")
    public Result<Payment> confirm(@PathVariable String paymentNo, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        Payment p = paymentService.confirmAtGateway(paymentNo, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "PAY_CONFIRM", "PAYMENT", p.getId(),
                "收银台确认支付 " + p.getBizType() + "#" + p.getBizId() + " ¥" + p.getAmount(), request.getRemoteAddr());
        return Result.success(p);
    }

    /** 模拟网关回调「支付成功」：仅管理员可重推（模拟网关重试/对账补偿），顾客支付一律走 /confirm。 */
    @PostMapping("/{paymentNo}/callback")
    public Result<Payment> callback(@PathVariable String paymentNo, HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if (!"admin".equals(role)) {
            return Result.error(403, "网关回调仅限服务端重推");
        }
        User currentUser = currentUser(request);
        Payment p = paymentService.callbackSuccess(paymentNo, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "PAY_CALLBACK", "PAYMENT", p.getId(),
                "支付成功 " + p.getBizType() + "#" + p.getBizId() + " ¥" + p.getAmount(), request.getRemoteAddr());
        return Result.success(p);
    }

    /** 查询支付流水（顾客仅可查自己的）。 */
    @GetMapping("/{paymentNo}")
    public Result<Payment> get(@PathVariable String paymentNo, HttpServletRequest request) {
        User currentUser = currentUser(request);
        String role = (String) request.getAttribute("currentUserRole");
        Payment p = paymentService.findByPaymentNo(paymentNo);
        if ("customer".equals(role) && !currentUser.getId().equals(p.getUserId())) {
            return Result.error(403, "无权查看该支付流水");
        }
        return Result.success(p);
    }

    /** 管理员/客服手动退款。 */
    @PostMapping("/{paymentNo}/refund")
    public Result<Payment> refund(@PathVariable String paymentNo, @RequestBody(required = false) Map<String, Object> body,
                                  HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if ("customer".equals(role)) {
            return Result.error(403, "顾客无权退款");
        }
        User currentUser = currentUser(request);
        BigDecimal refundAmount = body != null && body.get("refundAmount") != null
                ? new BigDecimal(String.valueOf(body.get("refundAmount"))) : null;
        Payment p = paymentService.refund(paymentNo, refundAmount);
        auditLogService.log(currentUser.getId(), role, "PAY_REFUND", "PAYMENT", p.getId(),
                "退款 ¥" + p.getRefundAmount(), request.getRemoteAddr());
        return Result.success(p);
    }

    private User currentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }
}

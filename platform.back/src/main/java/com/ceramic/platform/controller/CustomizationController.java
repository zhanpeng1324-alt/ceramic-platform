package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.Customization;
import com.ceramic.platform.entity.CustomizationProgress;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.AuditLogService;
import com.ceramic.platform.service.CustomizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customizations")
public class CustomizationController {
    private final CustomizationService customizationService;
    private final AuditLogService auditLogService;

    public CustomizationController(CustomizationService customizationService, AuditLogService auditLogService) {
        this.customizationService = customizationService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Result<List<Customization>> list(@RequestParam(required = false) Long userId, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        // admin/service 可按 userId 查看指定客户的定制单；顾客只能查自己的
        if (!"admin".equals(role) && !"service".equals(role)) {
            userId = currentUser.getId();
        }
        return Result.success(customizationService.list(userId));
    }

    @GetMapping("/{id}")
    public Result<Customization> detail(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        Customization c = customizationService.findById(id);
        if (c == null) return Result.error(404, "定制单不存在");
        if ("customer".equals(role) && !c.getUserId().equals(currentUser.getId())) {
            return Result.error(403, "无权查看他人定制单");
        }
        return Result.success(c);
    }

    @PostMapping
    public Result<Customization> create(@RequestBody Customization customization, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        customization.setUserId(currentUser.getId());
        Customization created = customizationService.create(customization);
        auditLogService.log(currentUser.getId(), role, "CREATE", "CUSTOMIZATION", created.getId(),
                "提交定制申请", request.getRemoteAddr());
        return Result.success(created);
    }

    /** 管理员报价：录入报价、预计完成日期、制作说明。 */
    @PatchMapping("/{id}/quote")
    public Result<Void> quote(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if (!"admin".equals(role)) return Result.error(403, "仅管理员可报价");
        User currentUser = (User) request.getAttribute("currentUser");
        BigDecimal quotedPrice = new BigDecimal(String.valueOf(body.get("quotedPrice")));
        String expectedCompleteDate = body.get("expectedCompleteDate") == null ? null : String.valueOf(body.get("expectedCompleteDate"));
        String timelineNote = body.get("timelineNote") == null ? null : String.valueOf(body.get("timelineNote"));
        customizationService.quote(id, quotedPrice, expectedCompleteDate, timelineNote, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "QUOTE",
                "CUSTOMIZATION", id, "报价 ¥" + quotedPrice, request.getRemoteAddr());
        return Result.success();
    }

    /** 客户确认报价。 */
    @PostMapping("/{id}/confirm-quote")
    public Result<Void> confirmQuote(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        customizationService.confirmQuote(id, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "CONFIRM_QUOTE",
                "CUSTOMIZATION", id, "确认报价", request.getRemoteAddr());
        return Result.success();
    }

    /** 客户支付定金（模拟支付）。 */
    @PostMapping("/{id}/pay-deposit")
    public Result<Void> payDeposit(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        customizationService.payDeposit(id, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "PAY_DEPOSIT",
                "CUSTOMIZATION", id, "支付定金", request.getRemoteAddr());
        return Result.success();
    }

    /** 客户支付定金成功后由支付网关驱动；此处保留客户主动验收入口：QUALITY_CHECK → COMPLETED（须已付尾款）。 */
    @PostMapping("/{id}/accept")
    public Result<Void> accept(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        customizationService.accept(id, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "ACCEPT",
                "CUSTOMIZATION", id, "客户验收定制成品", request.getRemoteAddr());
        return Result.success();
    }

    /** 客户/管理员拒绝报价。 */
    @PostMapping("/{id}/reject-quote")
    public Result<Void> rejectQuote(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                                    HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        String reason = body == null ? null : body.get("reason");
        customizationService.rejectQuote(id, currentUser.getId(), role, reason);
        auditLogService.log(currentUser.getId(), role, "REJECT_QUOTE",
                "CUSTOMIZATION", id, "拒绝报价" + (reason == null ? "" : ": " + reason), request.getRemoteAddr());
        return Result.success();
    }

    /** 取消定制（非终态）。 */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body,
                               HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        String reason = body == null ? null : body.get("reason");
        customizationService.cancel(id, currentUser.getId(), role, reason);
        auditLogService.log(currentUser.getId(), role, "CANCEL",
                "CUSTOMIZATION", id, "取消定制", request.getRemoteAddr());
        return Result.success();
    }

    /** 管理员通用状态流转（仅合法跳转），可附带说明与成品图。 */
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if (!"admin".equals(role)) return Result.error(403, "仅管理员可修改定制状态");
        User currentUser = (User) request.getAttribute("currentUser");
        String newStatus = String.valueOf(body.get("status"));
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        String imageUrl = body.get("imageUrl") == null ? null : String.valueOf(body.get("imageUrl"));
        customizationService.transitionStatus(id, newStatus, note, imageUrl, currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "UPDATE_STATUS",
                "CUSTOMIZATION", id, "定制单状态更新为: " + newStatus, request.getRemoteAddr());
        return Result.success();
    }

    @GetMapping("/{id}/progress")
    public Result<List<CustomizationProgress>> getProgress(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        String role = (String) request.getAttribute("currentUserRole");
        Customization c = customizationService.findById(id);
        if (c == null) return Result.error(404, "定制单不存在");
        if ("customer".equals(role) && !c.getUserId().equals(currentUser.getId())) {
            return Result.error(403, "无权查看");
        }
        return Result.success(customizationService.getProgress(id));
    }

    /** 管理员添加制作阶段进度说明。 */
    @PostMapping("/{id}/progress")
    public Result<Void> addProgress(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        if (!"admin".equals(role)) return Result.error(403, "仅管理员可添加进度");
        User currentUser = (User) request.getAttribute("currentUser");
        customizationService.addProgress(id, body.get("stage"), body.get("description"), body.get("imageUrl"),
                currentUser.getId(), role);
        auditLogService.log(currentUser.getId(), role, "ADD_PROGRESS",
                "CUSTOMIZATION", id, "添加进度: " + body.get("stage"), request.getRemoteAddr());
        return Result.success();
    }
}

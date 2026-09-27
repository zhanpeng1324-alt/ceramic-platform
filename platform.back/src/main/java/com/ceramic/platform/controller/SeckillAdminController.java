package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.SeckillActivity;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.AuditLogService;
import com.ceramic.platform.service.SeckillService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 秒杀管理端（仅 admin：AuthInterceptor 拦截 /api/admin/** 且 service/customer 均不放行）。
 * 创建/作废动作写审计日志。
 */
@RestController
@RequestMapping("/api/admin/seckill")
public class SeckillAdminController {

    private final SeckillService seckillService;
    private final AuditLogService auditLogService;

    public SeckillAdminController(SeckillService seckillService, AuditLogService auditLogService) {
        this.seckillService = seckillService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Result<List<SeckillActivity>> list() {
        return Result.success(seckillService.listActivities());
    }

    @PostMapping
    public Result<SeckillActivity> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Long productId = Long.valueOf(String.valueOf(body.get("productId")));
        BigDecimal price = new BigDecimal(String.valueOf(body.get("seckillPrice")));
        int stock = Integer.parseInt(String.valueOf(body.get("totalStock")));
        String start = String.valueOf(body.get("startTime"));
        String end = String.valueOf(body.get("endTime"));
        SeckillActivity a = seckillService.create(productId, price, stock, start, end);
        User op = (User) request.getAttribute("currentUser");
        auditLogService.log(op.getId(), (String) request.getAttribute("currentUserRole"),
                "CREATE", "SECKILL_ACTIVITY", a.getId(),
                "创建秒杀活动 productId=" + productId + " price=" + price + " stock=" + stock,
                request.getRemoteAddr());
        return Result.success(a);
    }

    @DeleteMapping("/{id}")
    public Result<?> cancel(@PathVariable Long id, HttpServletRequest request) {
        seckillService.offline(id);
        User op = (User) request.getAttribute("currentUser");
        auditLogService.log(op.getId(), (String) request.getAttribute("currentUserRole"),
                "CANCEL", "SECKILL_ACTIVITY", id, "下线秒杀活动（停止售卖并清预热数据）", request.getRemoteAddr());
        return Result.success();
    }
}

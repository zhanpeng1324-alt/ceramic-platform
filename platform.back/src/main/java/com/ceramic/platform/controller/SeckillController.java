package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.SeckillActivity;
import com.ceramic.platform.entity.SeckillOrder;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.SeckillService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 秒杀用户端。
 * 活动列表公开浏览；抢购/查单需登录且仅能操作本人订单。
 */
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    private final SeckillService seckillService;

    public SeckillController(SeckillService seckillService) {
        this.seckillService = seckillService;
    }

    /** 活动列表（含实时状态与抢购进度） */
    @GetMapping("/activities")
    public Result<List<SeckillActivity>> activities() {
        return Result.success(seckillService.listActivities());
    }

    @GetMapping("/activities/{id}")
    public Result<SeckillActivity> detail(@PathVariable Long id) {
        SeckillActivity a = seckillService.detail(id);
        if (a == null) return Result.fail("活动不存在");
        return Result.success(a);
    }

    /**
     * 抢购。成功返回流水号，订单异步落库，前端凭 orderNo 轮询 /orders/{orderNo} 获取结果。
     */
    @PostMapping("/{activityId}/buy")
    public Result<Map<String, String>> buy(@PathVariable Long activityId, HttpServletRequest request) {
        User u = currentUser(request);
        String orderNo = seckillService.buy(u.getId(), activityId);
        return Result.success(Map.of("orderNo", orderNo));
    }

    /** 我的秒杀单 */
    @GetMapping("/orders")
    public Result<List<SeckillOrder>> myOrders(HttpServletRequest request) {
        User u = currentUser(request);
        return Result.success(seckillService.myOrders(u.getId()));
    }

    /** 按流水号查单：抢购后轮询用；归属校验防越权 */
    @GetMapping("/orders/{orderNo}")
    public Result<SeckillOrder> orderDetail(@PathVariable String orderNo, HttpServletRequest request) {
        User u = currentUser(request);
        return Result.success(seckillService.orderDetail(u.getId(), orderNo));
    }

    private User currentUser(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }
}

package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.Review;
import com.ceramic.platform.service.AuditLogService;
import com.ceramic.platform.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.servlet.http.HttpServletRequest;
import com.ceramic.platform.entity.User;
import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;
    private final AuditLogService auditLogService;

    public ReviewController(ReviewService reviewService, AuditLogService auditLogService) {
        this.reviewService = reviewService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Result<List<Review>> list(@RequestParam Long productId) {
        return Result.success(reviewService.listByProduct(productId));
    }

    /** 当前登录用户评价过的商品 ID（前端据此判断订单是否「待评价」） */
    @GetMapping("/mine/products")
    public Result<List<Long>> myReviewedProducts(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) return Result.error(401, "请先登录");
        return Result.success(reviewService.listReviewedProductIds(currentUser.getId()));
    }

    @PostMapping
    public Result<Review> create(@RequestBody Review review, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) return Result.error(401, "请先登录");
        review.setUserId(currentUser.getId());
        Review created = reviewService.create(review);
        auditLogService.log(currentUser.getId(), currentUser.getRole(), "CREATE", "REVIEW",
                created.getId(), "发表商品评价", request.getRemoteAddr());
        return Result.success(created);
    }

    @GetMapping("/admin")
    public Result<List<Review>> listForAdmin(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null || !"admin".equals(user.getRole())) return Result.error(403, "仅管理员可查看全部评价");
        return Result.success(reviewService.listForAdmin());
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null || !"admin".equals(user.getRole())) return Result.error(403, "仅管理员可审核评价");
        reviewService.updateStatus(id, body.get("status"));
        auditLogService.log(user.getId(), user.getRole(), "UPDATE_STATUS", "REVIEW",
                id, "评价审核状态更新为: " + body.get("status"), request.getRemoteAddr());
        return Result.success();
    }

    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        if (user == null || !"admin".equals(user.getRole())) return Result.error(403, "仅管理员可回复评价");
        reviewService.reply(id, body.get("reply"));
        auditLogService.log(user.getId(), user.getRole(), "REPLY", "REVIEW",
                id, "回复评价", request.getRemoteAddr());
        return Result.success();
    }
}

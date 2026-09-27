package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.Recommendation;
import com.ceramic.platform.entity.RecommendationRule;
import com.ceramic.platform.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public Result<List<Recommendation>> getRecommendations(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                            @RequestParam(defaultValue = "6") Integer limit) {
        if (userId == null) userId = 1L;
        return Result.success(recommendationService.getRecommendations(userId, limit));
    }

    @GetMapping("/generate")
    public Result<List<Recommendation>> generateRecommendations(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                                @RequestParam(defaultValue = "6") Integer limit) {
        if (userId == null) userId = 1L;
        return Result.success(recommendationService.generateRecommendations(userId, limit));
    }

    @GetMapping("/similar/{productId}")
    public Result<List<Recommendation>> getSimilarProducts(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                           @PathVariable Long productId,
                                                           @RequestParam(defaultValue = "6") Integer limit) {
        if (userId == null) userId = 1L;
        return Result.success(recommendationService.getSimilarProducts(userId, productId, limit));
    }

    @GetMapping("/rules")
    public Result<List<RecommendationRule>> getRules(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(recommendationService.getAllRules());
    }

    @GetMapping("/rules/{id}")
    public Result<RecommendationRule> getRule(@PathVariable Long id, HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(recommendationService.getRule(id));
    }

    @PostMapping("/rules")
    public Result<RecommendationRule> createRule(@RequestBody RecommendationRule rule, HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(recommendationService.createRule(rule));
    }

    @PutMapping("/rules/{id}")
    public Result<RecommendationRule> updateRule(@PathVariable Long id, @RequestBody RecommendationRule rule, HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(recommendationService.updateRule(id, rule));
    }

    @PatchMapping("/rules/{id}/status")
    public Result<Void> toggleRule(@PathVariable Long id, @RequestParam Integer isActive, HttpServletRequest request) {
        requireAdmin(request);
        recommendationService.toggleRule(id, isActive);
        return Result.success();
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!"admin".equals(request.getAttribute("currentUserRole"))) {
            throw new IllegalArgumentException("Only administrators can manage recommendation rules");
        }
    }
}

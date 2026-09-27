package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.UserBehavior;
import com.ceramic.platform.entity.UserPreference;
import com.ceramic.platform.service.UserBehaviorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-behaviors")
public class UserBehaviorController {
    private final UserBehaviorService behaviorService;

    public UserBehaviorController(UserBehaviorService behaviorService) {
        this.behaviorService = behaviorService;
    }

    @PostMapping
    public Result<UserBehavior> record(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                        @RequestBody Map<String, Object> body) {
        Long productId = ((Number) body.get("productId")).longValue();
        String action = (String) body.get("action");
        Integer sessionDuration = body.get("sessionDuration") != null ? ((Number) body.get("sessionDuration")).intValue() : null;
        String referrer = (String) body.get("referrer");
        
        if (userId == null) userId = 1L;
        return Result.success(behaviorService.recordBehavior(userId, productId, action, sessionDuration, referrer));
    }

    @GetMapping
    public Result<List<UserBehavior>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                            @RequestParam(required = false) Long productId) {
        if (userId == null) userId = 1L;
        if (productId != null) {
            return Result.success(behaviorService.getByProductId(productId));
        }
        return Result.success(behaviorService.getByUserId(userId));
    }

    @GetMapping("/preferences")
    public Result<UserPreference> getPreferences(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) userId = 1L;
        return Result.success(behaviorService.getPreferences(userId));
    }
}
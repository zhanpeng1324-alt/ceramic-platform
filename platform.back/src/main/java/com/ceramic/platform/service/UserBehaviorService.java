package com.ceramic.platform.service;

import com.ceramic.platform.entity.UserBehavior;
import com.ceramic.platform.entity.UserPreference;
import com.ceramic.platform.mapper.UserBehaviorMapper;
import com.ceramic.platform.mapper.UserPreferenceMapper;
import com.ceramic.platform.mapper.ProductMapper;
import com.ceramic.platform.entity.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserBehaviorService {
    private final UserBehaviorMapper behaviorMapper;
    private final UserPreferenceMapper preferenceMapper;
    private final ProductMapper productMapper;

    public UserBehaviorService(UserBehaviorMapper behaviorMapper, UserPreferenceMapper preferenceMapper, ProductMapper productMapper) {
        this.behaviorMapper = behaviorMapper;
        this.preferenceMapper = preferenceMapper;
        this.productMapper = productMapper;
    }

    public UserBehavior recordBehavior(Long userId, Long productId, String action) {
        return recordBehavior(userId, productId, action, null, null);
    }

    public UserBehavior recordBehavior(Long userId, Long productId, String action, Integer sessionDuration, String referrer) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setProductId(productId);
        behavior.setAction(action);
        behavior.setSessionDuration(sessionDuration);
        behavior.setReferrer(referrer);
        behaviorMapper.insert(behavior);
        updateUserPreferences(userId);
        return behavior;
    }

    public List<UserBehavior> getByUserId(Long userId) {
        return behaviorMapper.findByUserId(userId);
    }

    public List<UserBehavior> getByProductId(Long productId) {
        return behaviorMapper.findByProductId(productId);
    }

    public Integer countByUserAndAction(Long userId, String action) {
        return behaviorMapper.countByUserAndAction(userId, action);
    }

    private void updateUserPreferences(Long userId) {
        List<UserBehavior> behaviors = behaviorMapper.findByUserId(userId);
        if (behaviors.isEmpty()) return;

        Map<Long, Integer> categoryCounts = new HashMap<>();
        BigDecimal minPrice = BigDecimal.valueOf(Double.MAX_VALUE);
        BigDecimal maxPrice = BigDecimal.ZERO;
        Map<String, Integer> materialCounts = new HashMap<>();
        Map<String, Integer> glazeColorCounts = new HashMap<>();

        for (UserBehavior behavior : behaviors) {
            Product product = productMapper.findById(behavior.getProductId());
            if (product == null) continue;

            if (product.getCategoryId() != null) {
                categoryCounts.put(product.getCategoryId(), categoryCounts.getOrDefault(product.getCategoryId(), 0) + 1);
            }

            if (product.getPrice() != null) {
                if (product.getPrice().compareTo(minPrice) < 0) {
                    minPrice = product.getPrice();
                }
                if (product.getPrice().compareTo(maxPrice) > 0) {
                    maxPrice = product.getPrice();
                }
            }

            if (product.getMaterial() != null && !product.getMaterial().isBlank()) {
                materialCounts.put(product.getMaterial(), materialCounts.getOrDefault(product.getMaterial(), 0) + 1);
            }

            if (product.getGlazeColor() != null && !product.getGlazeColor().isBlank()) {
                glazeColorCounts.put(product.getGlazeColor(), glazeColorCounts.getOrDefault(product.getGlazeColor(), 0) + 1);
            }
        }

        UserPreference existing = preferenceMapper.findByUserId(userId);
        UserPreference preference = existing != null ? existing : new UserPreference();
        preference.setUserId(userId);

        if (!categoryCounts.isEmpty()) {
            Long mostFrequentCategory = categoryCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            preference.setCategoryId(mostFrequentCategory);
        }

        if (minPrice.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) < 0) {
            preference.setPreferredPriceMin(minPrice.multiply(BigDecimal.valueOf(0.5)));
            preference.setPreferredPriceMax(maxPrice.multiply(BigDecimal.valueOf(1.5)));
        }

        if (!materialCounts.isEmpty()) {
            String mostFrequentMaterial = materialCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            preference.setPreferredMaterial(mostFrequentMaterial);
        }

        if (!glazeColorCounts.isEmpty()) {
            String mostFrequentGlazeColor = glazeColorCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            preference.setPreferredGlazeColor(mostFrequentGlazeColor);
        }

        if (existing != null) {
            preferenceMapper.update(preference);
        } else {
            preferenceMapper.insert(preference);
        }
    }

    public UserPreference getPreferences(Long userId) {
        return preferenceMapper.findByUserId(userId);
    }
}
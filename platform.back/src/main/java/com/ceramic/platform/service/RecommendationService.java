package com.ceramic.platform.service;

import com.ceramic.platform.entity.Product;
import com.ceramic.platform.entity.Recommendation;
import com.ceramic.platform.entity.RecommendationRule;
import com.ceramic.platform.entity.Review;
import com.ceramic.platform.entity.UserBehavior;
import com.ceramic.platform.entity.UserPreference;
import com.ceramic.platform.mapper.RecommendationMapper;
import com.ceramic.platform.mapper.RecommendationRuleMapper;
import com.ceramic.platform.mapper.ProductMapper;
import com.ceramic.platform.mapper.ReviewMapper;
import com.ceramic.platform.mapper.UserBehaviorMapper;
import com.ceramic.platform.mapper.UserPreferenceMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private final RecommendationMapper recommendationMapper;
    private final RecommendationRuleMapper ruleMapper;
    private final ProductMapper productMapper;
    private final ReviewMapper reviewMapper;
    private final UserBehaviorMapper behaviorMapper;
    private final UserPreferenceMapper preferenceMapper;

    public RecommendationService(RecommendationMapper recommendationMapper, 
                                  RecommendationRuleMapper ruleMapper,
                                  ProductMapper productMapper,
                                  ReviewMapper reviewMapper,
                                  UserBehaviorMapper behaviorMapper,
                                  UserPreferenceMapper preferenceMapper) {
        this.recommendationMapper = recommendationMapper;
        this.ruleMapper = ruleMapper;
        this.productMapper = productMapper;
        this.reviewMapper = reviewMapper;
        this.behaviorMapper = behaviorMapper;
        this.preferenceMapper = preferenceMapper;
    }

    public List<Recommendation> getRecommendations(Long userId, Integer limit) {
        limit = normalizeLimit(limit);
        List<Recommendation> existing = recommendationMapper.findTopByUserId(userId, limit);
        if (!existing.isEmpty()) {
            return existing;
        }
        return generateRecommendations(userId, limit);
    }

    public List<Recommendation> generateRecommendations(Long userId, Integer limit) {
        limit = normalizeLimit(limit);
        recommendationMapper.deleteByUserId(userId);
        
        List<Recommendation> recommendations = new ArrayList<>();
        
        List<RecommendationRule> activeRules = ruleMapper.findActiveRules();
        if (activeRules.isEmpty()) {
            activeRules = Arrays.asList(
                createDefaultRule("热门推荐", "popularity"),
                createDefaultRule("个性化推荐", "personalized"),
                createDefaultRule("趋势推荐", "trending")
            );
        }

        for (RecommendationRule rule : activeRules) {
            List<Recommendation> ruleRecs = generateByRule(userId, rule, limit);
            recommendations.addAll(ruleRecs);
        }

        recommendations.sort((a, b) -> b.getScore().compareTo(a.getScore()));
        
        Set<Long> seenProducts = new HashSet<>();
        List<Recommendation> uniqueRecs = new ArrayList<>();
        for (Recommendation rec : recommendations) {
            if (!seenProducts.contains(rec.getProductId())) {
                seenProducts.add(rec.getProductId());
                recommendationMapper.insert(rec);
                uniqueRecs.add(rec);
                if (uniqueRecs.size() >= limit) break;
            }
        }

        if (uniqueRecs.size() < limit) {
            List<Product> allProducts = productMapper.findAll();
            String[] reasons = {"popular", "personalized", "trending", "category_match"};
            int reasonIndex = 0;
            for (Product product : allProducts) {
                if (!seenProducts.contains(product.getId())) {
                    Recommendation rec = new Recommendation();
                    rec.setUserId(userId);
                    rec.setProductId(product.getId());
                    rec.setReason(reasons[reasonIndex % reasons.length]);
                    rec.setScore(BigDecimal.valueOf(0.05).setScale(4, RoundingMode.HALF_UP));
                    recommendationMapper.insert(rec);
                    uniqueRecs.add(rec);
                    reasonIndex++;
                    if (uniqueRecs.size() >= limit) break;
                }
            }
        }

        return uniqueRecs;
    }

    public List<Recommendation> getSimilarProducts(Long userId, Long productId, Integer limit) {
        limit = normalizeLimit(limit);
        Product currentProduct = productMapper.findById(productId);
        if (currentProduct == null) return Collections.emptyList();

        List<Product> allProducts = productMapper.findAll();
        List<Product> similarProducts = allProducts.stream()
                .filter(p -> !p.getId().equals(productId))
                .filter(p -> p.getCategoryId().equals(currentProduct.getCategoryId()))
                .sorted((a, b) -> Double.compare(calculateSimilarity(currentProduct, b), calculateSimilarity(currentProduct, a)))
                .limit(limit)
                .collect(Collectors.toList());

        List<Recommendation> recommendations = new ArrayList<>();
        for (int i = 0; i < similarProducts.size(); i++) {
            Recommendation rec = new Recommendation();
            rec.setUserId(userId);
            rec.setProductId(similarProducts.get(i).getId());
            rec.setReason("similar");
            rec.setScore(BigDecimal.valueOf(1 - (double) i / similarProducts.size()).setScale(4, RoundingMode.HALF_UP));
            recommendations.add(rec);
        }

        return recommendations;
    }

    private List<Recommendation> generateByRule(Long userId, RecommendationRule rule, Integer limit) {
        List<Recommendation> recommendations = new ArrayList<>();
        
        switch (rule.getType()) {
            case "popularity":
                recommendations = generatePopularRecommendations(userId, limit);
                break;
            case "personalized":
                recommendations = generatePersonalizedRecommendations(userId, limit);
                break;
            case "trending":
                recommendations = generateTrendingRecommendations(userId, limit);
                break;
            case "category":
                recommendations = generateCategoryRecommendations(userId, limit);
                break;
            default:
                recommendations = generatePopularRecommendations(userId, limit);
        }

        double weight = rule.getWeight().doubleValue();
        for (Recommendation rec : recommendations) {
            rec.setScore(rec.getScore().multiply(BigDecimal.valueOf(weight)).setScale(4, RoundingMode.HALF_UP));
        }

        return recommendations;
    }

    private List<Recommendation> generatePopularRecommendations(Long userId, Integer limit) {
        List<Product> products = productMapper.findAll();
        List<Review> reviews = reviewMapper.findAll();
        
        Map<Long, Double> popularityScores = new HashMap<>();
        for (Product product : products) {
            double score = 0;
            List<Review> productReviews = reviews.stream()
                    .filter(r -> r.getProductId().equals(product.getId()))
                    .collect(Collectors.toList());
            
            if (!productReviews.isEmpty()) {
                double avgRating = productReviews.stream()
                        .mapToInt(Review::getRating)
                        .average()
                        .orElse(0);
                score += avgRating * 0.5;
                score += productReviews.size() * 0.1;
            }
            
            if (product.getStock() != null && product.getStock() > 0) {
                score += 0.05;
            }
            
            if (score == 0) {
                score = 0.1;
            }
            
            popularityScores.put(product.getId(), score);
        }

        List<Product> popularProducts = products.stream()
                .sorted((a, b) -> Double.compare(popularityScores.get(b.getId()), popularityScores.get(a.getId())))
                .limit(limit)
                .collect(Collectors.toList());

        return createRecommendations(userId, popularProducts, "popular");
    }

    private List<Recommendation> generatePersonalizedRecommendations(Long userId, Integer limit) {
        UserPreference preference = preferenceMapper.findByUserId(userId);
        List<UserBehavior> behaviors = behaviorMapper.findByUserId(userId);
        
        if (preference == null && behaviors.isEmpty()) {
            return generatePopularRecommendations(userId, limit);
        }

        List<Product> products = productMapper.findAll();
        Map<Long, Double> scores = new HashMap<>();

        for (Product product : products) {
            double score = 0;
            
            if (preference != null) {
                if (preference.getCategoryId() != null && preference.getCategoryId().equals(product.getCategoryId())) {
                    score += 0.4;
                }
                
                if (preference.getPreferredPriceMin() != null && preference.getPreferredPriceMax() != null) {
                    if (product.getPrice().compareTo(preference.getPreferredPriceMin()) >= 0 &&
                        product.getPrice().compareTo(preference.getPreferredPriceMax()) <= 0) {
                        score += 0.3;
                    }
                }
                
                if (preference.getPreferredMaterial() != null && 
                    preference.getPreferredMaterial().equals(product.getMaterial())) {
                    score += 0.2;
                }
                
                if (preference.getPreferredGlazeColor() != null && 
                    preference.getPreferredGlazeColor().equals(product.getGlazeColor())) {
                    score += 0.1;
                }
            }

            for (UserBehavior behavior : behaviors) {
                Product behaviorProduct = productMapper.findById(behavior.getProductId());
                if (behaviorProduct != null && behaviorProduct.getCategoryId().equals(product.getCategoryId())) {
                    if ("view".equals(behavior.getAction())) score += 0.05;
                    if ("add_to_cart".equals(behavior.getAction())) score += 0.1;
                    if ("purchase".equals(behavior.getAction())) score += 0.2;
                }
            }

            scores.put(product.getId(), score);
        }

        List<Product> recommended = products.stream()
                .filter(p -> scores.get(p.getId()) > 0)
                .sorted((a, b) -> Double.compare(scores.get(b.getId()), scores.get(a.getId())))
                .limit(limit)
                .collect(Collectors.toList());

        return createRecommendations(userId, recommended, "personalized");
    }

    private List<Recommendation> generateTrendingRecommendations(Long userId, Integer limit) {
        List<Product> products = productMapper.findAll();
        List<Review> reviews = reviewMapper.findAll();
        
        Map<Long, Double> trendingScores = new HashMap<>();
        Map<Long, Long> recentReviewCounts = reviews.stream()
                .collect(Collectors.groupingBy(Review::getProductId, Collectors.counting()));

        for (Product product : products) {
            double score = recentReviewCounts.getOrDefault(product.getId(), 0L) * 0.1;
            
            if (product.getStock() != null && product.getStock() > 0) {
                score += 0.05;
            }
            
            if (score == 0) {
                score = 0.1;
            }
            
            trendingScores.put(product.getId(), score);
        }

        List<Product> trending = products.stream()
                .sorted((a, b) -> Double.compare(trendingScores.get(b.getId()), trendingScores.get(a.getId())))
                .limit(limit)
                .collect(Collectors.toList());

        return createRecommendations(userId, trending, "trending");
    }

    private List<Recommendation> generateCategoryRecommendations(Long userId, Integer limit) {
        UserPreference preference = preferenceMapper.findByUserId(userId);
        if (preference == null || preference.getCategoryId() == null) {
            return generatePopularRecommendations(userId, limit);
        }

        List<Product> products = productMapper.findAll();
        List<Product> categoryProducts = products.stream()
                .filter(p -> preference.getCategoryId().equals(p.getCategoryId()))
                .limit(limit)
                .collect(Collectors.toList());

        return createRecommendations(userId, categoryProducts, "category_match");
    }

    private List<Recommendation> createRecommendations(Long userId, List<Product> products, String reason) {
        List<Recommendation> recommendations = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            Recommendation rec = new Recommendation();
            rec.setUserId(userId);
            rec.setProductId(products.get(i).getId());
            rec.setReason(reason);
            rec.setScore(BigDecimal.valueOf(1 - (double) i / products.size()).setScale(4, RoundingMode.HALF_UP));
            recommendations.add(rec);
        }
        return recommendations;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) return 6;
        return Math.max(1, Math.min(limit, 24));
    }

    private double calculateSimilarity(Product p1, Product p2) {
        double score = 0;
        
        if (p1.getCategoryId().equals(p2.getCategoryId())) score += 0.4;
        
        if (p1.getPrice() != null && p2.getPrice() != null) {
            double priceDiff = Math.abs(p1.getPrice().doubleValue() - p2.getPrice().doubleValue()) 
                    / Math.max(p1.getPrice().doubleValue(), p2.getPrice().doubleValue());
            if (priceDiff < 0.3) score += 0.3;
        }
        
        if (p1.getMaterial() != null && p2.getMaterial() != null && p1.getMaterial().equals(p2.getMaterial())) {
            score += 0.2;
        }
        
        if (p1.getCustomizable() != null && p2.getCustomizable() != null && p1.getCustomizable().equals(p2.getCustomizable())) {
            score += 0.1;
        }
        
        return score;
    }

    private RecommendationRule createDefaultRule(String name, String type) {
        RecommendationRule rule = new RecommendationRule();
        rule.setName(name);
        rule.setType(type);
        rule.setWeight(BigDecimal.ONE);
        rule.setIsActive(1);
        return rule;
    }

    public List<RecommendationRule> getAllRules() {
        return ruleMapper.findAll();
    }

    public RecommendationRule getRule(Long id) {
        return ruleMapper.findById(id);
    }

    public RecommendationRule createRule(RecommendationRule rule) {
        rule.setIsActive(rule.getIsActive() == null ? 1 : rule.getIsActive());
        rule.setWeight(rule.getWeight() == null ? BigDecimal.ONE : rule.getWeight());
        ruleMapper.insert(rule);
        return rule;
    }

    public RecommendationRule updateRule(Long id, RecommendationRule rule) {
        RecommendationRule existing = ruleMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("规则不存在");
        }
        rule.setId(id);
        ruleMapper.update(rule);
        return ruleMapper.findById(id);
    }

    public void toggleRule(Long id, Integer isActive) {
        ruleMapper.updateStatus(id, isActive);
    }
}

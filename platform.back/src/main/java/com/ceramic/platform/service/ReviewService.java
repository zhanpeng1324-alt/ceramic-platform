package com.ceramic.platform.service;

import com.ceramic.platform.entity.Review;
import com.ceramic.platform.mapper.OrderMapper;
import com.ceramic.platform.mapper.ReviewMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {
    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;

    public ReviewService(ReviewMapper reviewMapper, OrderMapper orderMapper) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
    }

    public List<Review> listByProduct(Long productId) {
        return reviewMapper.findByProductId(productId);
    }

    /** 当前用户评价过的商品 ID 列表，供前端「待评价」过滤 */
    public List<Long> listReviewedProductIds(Long userId) {
        return reviewMapper.findReviewedProductIds(userId);
    }

    public boolean hasPurchased(Long userId, Long productId) {
        // 评价资格：需有「已完成」订单，仅付款/发货尚不足以评价
        Integer count = orderMapper.countCompletedUserProductOrders(userId, productId);
        return count != null && count > 0;
    }

    public Review create(Review review) {
        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            throw new IllegalArgumentException("评分必须在 1 到 5 之间");
        }
        if (!hasPurchased(review.getUserId(), review.getProductId())) {
            throw new IllegalArgumentException("只有完成该商品订单的用户才能评价");
        }
        Integer reviewed = reviewMapper.countByUserAndProduct(review.getUserId(), review.getProductId());
        if (reviewed != null && reviewed > 0) {
            throw new IllegalArgumentException("您已评价过该商品，请勿重复评价");
        }
        review.setStatus("VISIBLE");
        reviewMapper.insert(review);
        return review;
    }

    public List<Review> listForAdmin() {
        return reviewMapper.findAllForAdmin();
    }

    public void updateStatus(Long id, String status) {
        if (!List.of("VISIBLE", "HIDDEN", "REJECTED").contains(status)) {
            throw new IllegalArgumentException("无效的评价状态");
        }
        reviewMapper.updateStatus(id, status);
    }

    public void reply(Long id, String reply) {
        if (reply == null || reply.trim().isEmpty()) {
            throw new IllegalArgumentException("回复内容不能为空");
        }
        reviewMapper.reply(id, reply.trim());
    }
}

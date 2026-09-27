package com.ceramic.platform.dto;

import com.ceramic.platform.entity.Product;
import com.ceramic.platform.entity.Review;
import lombok.Data;

import java.util.List;

@Data
public class ProductDetailResponse {
    private Product product;
    private Integer salesCount;
    private List<Review> reviews;
    private Double averageRating;
    private Integer reviewCount;
    private Boolean canReview;
}
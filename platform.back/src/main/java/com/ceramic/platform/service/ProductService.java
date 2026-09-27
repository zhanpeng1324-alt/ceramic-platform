package com.ceramic.platform.service;

import com.ceramic.platform.dto.ProductDetailResponse;
import com.ceramic.platform.entity.Product;
import com.ceramic.platform.entity.Review;
import com.ceramic.platform.mapper.OrderMapper;
import com.ceramic.platform.mapper.ProductMapper;
import com.ceramic.platform.mapper.ReviewMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductService {
    private final ProductMapper productMapper;
    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final ProductCache productCache;

    public ProductService(ProductMapper productMapper, ReviewMapper reviewMapper,
                          OrderMapper orderMapper, ProductCache productCache) {
        this.productMapper = productMapper;
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.productCache = productCache;
    }

    public List<Product> search(Long categoryId, String keyword,
                                BigDecimal priceMin, BigDecimal priceMax, String sort) {
        List<Product> items = productMapper.search(categoryId, keyword, priceMin, priceMax, sort);
        fillSales(items);
        return items;
    }

    /** 分页搜索：返回 items + total，供列表页按页拉取 */
    public Map<String, Object> searchPage(Long categoryId, String keyword,
                                          BigDecimal priceMin, BigDecimal priceMax, String sort,
                                          int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 1);
        long total = productMapper.countSearch(categoryId, keyword, priceMin, priceMax);
        List<Product> items = productMapper.searchPage(categoryId, keyword, priceMin, priceMax, sort,
                (long) (safePage - 1) * safeSize, safeSize);
        fillSales(items);
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    /**
     * 批量填充已售件数：一次聚合查询覆盖整页商品，避免 N+1。
     * 销量口径与排序、详情页一致：仅统计已付款/已发货/已完成订单，取消单不计入。
     */
    private void fillSales(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, Integer> salesMap = new HashMap<>();
        for (Map<String, Object> row : productMapper.salesByProductIds(ids)) {
            Object pid = row.get("product_id");
            Object sold = row.get("sold");
            if (pid instanceof Number nPid && sold instanceof Number nSold) {
                salesMap.put(nPid.longValue(), nSold.intValue());
            }
        }
        for (Product p : products) {
            p.setSales(salesMap.getOrDefault(p.getId(), 0));
        }
    }

    /** 管理端列表：含已下架商品，已下架排在后面 */
    public List<Product> adminList(Long categoryId, String keyword) {
        return productMapper.adminSearch(categoryId, keyword);
    }

    public Product findById(Long id) {
        return productMapper.findById(id);
    }

    public ProductDetailResponse detail(Long id, Long userId) {
        // 商品本体走 Redis 缓存（公共数据）；评论/销量/能否评价是实时或千人千面数据，永远直查
        Product product = productCache.get(id);
        if (product == null) {
            throw new IllegalArgumentException("商品不存在");
        }

        ProductDetailResponse response = new ProductDetailResponse();
        response.setProduct(product);
        response.setSalesCount(productMapper.getSalesCount(id));
        
        List<Review> reviews = reviewMapper.findByProductId(id);
        response.setReviews(reviews);
        response.setReviewCount(reviews.size());
        
        if (!reviews.isEmpty()) {
            double totalRating = reviews.stream().mapToInt(Review::getRating).sum();
            response.setAverageRating(Math.round(totalRating / reviews.size() * 10) / 10.0);
        } else {
            response.setAverageRating(0.0);
        }

        if (userId != null) {
            // 可评价 = 有已完成订单 且 尚未评价过
            Integer purchased = orderMapper.countCompletedUserProductOrders(userId, id);
            Integer reviewed = reviewMapper.countByUserAndProduct(userId, id);
            boolean canReview = purchased != null && purchased > 0
                    && (reviewed == null || reviewed == 0);
            response.setCanReview(canReview);
        } else {
            response.setCanReview(false);
        }

        return response;
    }

    public Product create(Product product) {
        validate(product);
        if (product.getStatus() == null || product.getStatus().isBlank()) {
            product.setStatus("ON_SALE");
        }
        if (product.getCustomizable() == null) {
            product.setCustomizable(false);
        }
        productMapper.insert(product);
        // 新商品 id 加入布隆过滤器，保证穿透拦截覆盖新品
        productCache.onProductCreated(product.getId());
        return product;
    }

    public Product update(Long id, Product product) {
        validate(product);
        product.setId(id);
        if (productMapper.update(product) == 0) {
            throw new IllegalArgumentException("商品不存在");
        }
        productCache.evict(id);
        return productMapper.findById(id);
    }

    public void delete(Long id) {
        productMapper.delete(id);
        productCache.evict(id);
    }

    /** 上架/下架：只改 status 一列，不触发整行校验与覆盖 */
    public void updateStatus(Long id, String status) {
        if (!List.of("active", "inactive").contains(status)) {
            throw new IllegalArgumentException("无效的商品状态");
        }
        if (productMapper.updateStatus(id, status) == 0) {
            throw new IllegalArgumentException("商品不存在");
        }
        productCache.evict(id);
    }

    private void validate(Product product) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (product.getPrice() == null) {
            throw new IllegalArgumentException("商品价格不能为空");
        }
        if (product.getStock() == null || product.getStock() < 0) {
            throw new IllegalArgumentException("库存不能小于 0");
        }
    }
}
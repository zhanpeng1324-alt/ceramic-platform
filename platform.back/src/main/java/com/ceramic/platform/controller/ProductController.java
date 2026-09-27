package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.dto.ProductDetailResponse;
import com.ceramic.platform.entity.Product;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.AuditLogService;
import com.ceramic.platform.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;
    private final AuditLogService auditLogService;

    public ProductController(ProductService productService, AuditLogService auditLogService) {
        this.productService = productService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Result<?> list(@RequestParam(required = false) Long categoryId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) BigDecimal priceMin,
                          @RequestParam(required = false) BigDecimal priceMax,
                          @RequestParam(required = false) String sort,
                          @RequestParam(required = false) Integer page,
                          @RequestParam(required = false) Integer size) {
        // 带 page/size 走分页结构 {items,total,page,size}；不带给旧调用方保持全量 List 兼容
        if (page != null || size != null) {
            int p = page != null ? page : 1;
            int s = size != null ? size : 20;
            return Result.success(productService.searchPage(categoryId, keyword, priceMin, priceMax, sort, p, s));
        }
        return Result.success(productService.search(categoryId, keyword, priceMin, priceMax, sort));
    }

    @GetMapping("/admin")
    public Result<List<Product>> adminList(@RequestParam(required = false) Long categoryId,
                                           @RequestParam(required = false) String keyword,
                                           HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(productService.adminList(categoryId, keyword));
    }

    @GetMapping("/{id}")
    public Result<ProductDetailResponse> detail(@PathVariable Long id, HttpServletRequest request) {
        Long userId = null;
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser != null) {
            userId = currentUser.getId();
        }
        return Result.success(productService.detail(id, userId));
    }

    @PostMapping
    public Result<Product> create(@RequestBody Product product, HttpServletRequest request) {
        User currentUser = requireAdmin(request);
        Product created = productService.create(product);
        auditLogService.log(currentUser.getId(), "admin", "CREATE",
                "PRODUCT", created.getId(), "创建商品: " + product.getName(), request.getRemoteAddr());
        return Result.success(created);
    }

    @PutMapping("/{id}")
    public Result<Product> update(@PathVariable Long id, @RequestBody Product product, HttpServletRequest request) {
        User currentUser = requireAdmin(request);
        product.setId(id);
        Product updated = productService.update(id, product);
        auditLogService.log(currentUser.getId(), "admin", "UPDATE",
                "PRODUCT", id, "更新商品: " + product.getName(), request.getRemoteAddr());
        return Result.success(updated);
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body,
                                     HttpServletRequest request) {
        User currentUser = requireAdmin(request);
        String status = body.get("status");
        productService.updateStatus(id, status);
        auditLogService.log(currentUser.getId(), "admin", "UPDATE",
                "PRODUCT", id, "更新商品状态: " + status, request.getRemoteAddr());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = requireAdmin(request);
        productService.delete(id);
        auditLogService.log(currentUser.getId(), "admin", "DELETE",
                "PRODUCT", id, "删除商品ID: " + id, request.getRemoteAddr());
        return Result.success();
    }

    private User requireAdmin(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null || !"admin".equals(currentUser.getRole())) {
            throw new IllegalArgumentException("仅管理员可执行此操作");
        }
        return currentUser;
    }
}

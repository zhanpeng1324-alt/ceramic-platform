package com.ceramic.platform.service;

import com.ceramic.platform.entity.CartItem;
import com.ceramic.platform.entity.Product;
import com.ceramic.platform.mapper.CartMapper;
import com.ceramic.platform.mapper.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CartService {
    private final CartMapper cartMapper;
    private final ProductMapper productMapper;

    public CartService(CartMapper cartMapper, ProductMapper productMapper) {
        this.cartMapper = cartMapper;
        this.productMapper = productMapper;
    }

    public List<CartItem> list(Long userId) {
        requireUser(userId);
        return cartMapper.findByUserId(userId);
    }

    public CartItem add(CartItem item) {
        requireUser(item.getUserId());
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            item.setQuantity(1);
        }
        if (item.getProductId() != null) {
            Product product = productMapper.findById(item.getProductId());
            if (product != null) {
                item.setProductName(product.getName());
                item.setPrice(product.getPrice());
                if (item.getImageUrl() == null || item.getImageUrl().isEmpty()) {
                    item.setImageUrl(product.getImageUrl());
                }
            }
            CartItem existing = cartMapper.findExisting(item.getUserId(), item.getProductId());
            if (existing != null) {
                existing.setQuantity(existing.getQuantity() + item.getQuantity());
                if (item.getSelectedOptions() != null) {
                    existing.setSelectedOptions(item.getSelectedOptions());
                }
                cartMapper.update(existing);
                return existing;
            }
        }
        cartMapper.insert(item);
        return item;
    }

    public CartItem update(Long id, CartItem item, Long userId) {
        requireUser(userId);
        CartItem existing = cartMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("购物车项不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权修改他人购物车");
        }
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new IllegalArgumentException("数量必须大于 0");
        }
        item.setId(id);
        item.setUserId(userId);
        cartMapper.update(item);
        return item;
    }

    public void delete(Long id, Long userId) {
        requireUser(userId);
        CartItem existing = cartMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("购物车项不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除他人购物车");
        }
        cartMapper.delete(id);
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
    }
}

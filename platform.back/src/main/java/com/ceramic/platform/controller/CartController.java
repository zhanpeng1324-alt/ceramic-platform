package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.CartItem;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Result<List<CartItem>> list(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        return Result.success(cartService.list(currentUser.getId()));
    }

    @PostMapping
    public Result<CartItem> add(@RequestBody CartItem item, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        item.setUserId(currentUser.getId());
        return Result.success(cartService.add(item));
    }

    @PutMapping("/{id}")
    public Result<CartItem> update(@PathVariable Long id, @RequestBody CartItem item, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        return Result.success(cartService.update(id, item, currentUser.getId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        cartService.delete(id, currentUser.getId());
        return Result.success();
    }
}

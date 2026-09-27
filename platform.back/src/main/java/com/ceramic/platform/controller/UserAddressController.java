package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.entity.UserAddress;
import com.ceramic.platform.service.UserAddressService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class UserAddressController {

    private final UserAddressService userAddressService;

    public UserAddressController(UserAddressService userAddressService) {
        this.userAddressService = userAddressService;
    }

    @PostMapping
    public Result<UserAddress> add(@RequestBody UserAddress address, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        address.setUserId(currentUser.getId());
        return Result.success(userAddressService.addAddress(address));
    }

    @GetMapping
    public Result<List<UserAddress>> list(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        return Result.success(userAddressService.getAddresses(currentUser.getId()));
    }

    @GetMapping("/default")
    public Result<UserAddress> getDefault(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        return Result.success(userAddressService.getDefaultAddress(currentUser.getId()));
    }

    @GetMapping("/{id}")
    public Result<UserAddress> get(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        UserAddress address = userAddressService.getAddress(id);
        if (address == null) {
            return Result.error(404, "地址不存在");
        }
        if (!currentUser.getRole().equals("admin") && !address.getUserId().equals(currentUser.getId())) {
            return Result.error(403, "无权查看该地址");
        }
        return Result.success(address);
    }

    @PutMapping("/{id}")
    public Result<UserAddress> update(@PathVariable Long id, @RequestBody UserAddress address, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        UserAddress existing = userAddressService.getAddress(id);
        if (existing == null) {
            return Result.error(404, "地址不存在");
        }
        if (!currentUser.getRole().equals("admin") && !existing.getUserId().equals(currentUser.getId())) {
            return Result.error(403, "无权修改该地址");
        }
        address.setId(id);
        address.setUserId(existing.getUserId());
        return Result.success(userAddressService.updateAddress(address));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        UserAddress existing = userAddressService.getAddress(id);
        if (existing == null) {
            return Result.error(404, "地址不存在");
        }
        if (!currentUser.getRole().equals("admin") && !existing.getUserId().equals(currentUser.getId())) {
            return Result.error(403, "无权删除该地址");
        }
        userAddressService.deleteAddress(id);
        return Result.success();
    }

    @PostMapping("/{id}/default")
    public Result<UserAddress> setDefault(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        UserAddress existing = userAddressService.getAddress(id);
        if (existing == null) {
            return Result.error(404, "地址不存在");
        }
        if (!currentUser.getRole().equals("admin") && !existing.getUserId().equals(currentUser.getId())) {
            return Result.error(403, "无权操作该地址");
        }
        return Result.success(userAddressService.setDefault(currentUser.getId(), id));
    }
}

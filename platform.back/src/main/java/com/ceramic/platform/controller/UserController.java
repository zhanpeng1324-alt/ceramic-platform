package com.ceramic.platform.controller;

import com.ceramic.platform.common.Result;
import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.SmsCodeService;
import com.ceramic.platform.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final SmsCodeService smsCodeService;

    public UserController(UserService userService, SmsCodeService smsCodeService) {
        this.userService = userService;
        this.smsCodeService = smsCodeService;
    }

    @GetMapping
    public Result<List<User>> list(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(userService.listAllUsers());
    }

    @GetMapping("/customers")
    public Result<List<User>> listCustomers(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(userService.listCustomers());
    }

    @GetMapping("/service")
    public Result<List<User>> listServiceUsers(HttpServletRequest request) {
        requireAdmin(request);
        return Result.success(userService.listServiceUsers());
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String code = body.get("code");
        if (phone == null || phone.isBlank()) {
            return Result.error(400, "手机号不能为空");
        }
        if (!phone.matches("\\d{11}")) {
            return Result.error(400, "请输入正确的11位手机号");
        }
        if (code == null || !code.matches("\\d{6}")) {
            return Result.error(400, "请输入6位短信验证码");
        }
        // 注册只需手机号 + 短信验证码（密码可后续通过验证码登录，不再强制设置）
        smsCodeService.verify(phone, code);
        Map<String, Object> result = userService.registerByPhone(phone, body.get("password"));
        return Result.success(result);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String account = body.get("username");
        String password = body.get("password");
        Map<String, Object> result;
        if (account != null && account.matches("\\d{11}")) {
            result = userService.loginByPhone(account, password);
        } else {
            result = userService.login(account, password);
        }
        return Result.success(result);
    }

    @PostMapping("/service/login")
    public Result<Map<String, Object>> loginService(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        Map<String, Object> result = userService.loginService(username, password);
        return Result.success(result);
    }

    /** 发送短信验证码（演示模式验证码回显，生产环境由真实短信通道下发） */
    @PostMapping("/sms/send")
    public Result<Map<String, Object>> sendSmsCode(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone == null || !phone.matches("\\d{11}")) {
            return Result.error(400, "请输入正确的11位手机号");
        }
        return Result.success(smsCodeService.send(phone));
    }

    /** 短信验证码登录：未注册手机号自动创建账号 */
    @PostMapping("/sms/login")
    public Result<Map<String, Object>> smsLogin(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String code = body.get("code");
        if (phone == null || !phone.matches("\\d{11}")) {
            return Result.error(400, "请输入正确的11位手机号");
        }
        if (code == null || !code.matches("\\d{6}")) {
            return Result.error(400, "请输入6位验证码");
        }
        smsCodeService.verify(phone, code);
        return Result.success(userService.loginOrRegisterBySms(phone));
    }

    @GetMapping("/profile")
    public Result<User> profile(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(userService.getById(currentUser.getId()));
    }

    @PutMapping("/profile")
    public Result<User> updateProfile(@RequestBody User user, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            return Result.error(401, "未登录");
        }
        return Result.success(userService.updateProfile(currentUser.getId(), user));
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            return Result.error(401, "未登录");
        }
        if (!currentUser.getRole().equals("admin") && !currentUser.getId().equals(id)) {
            return Result.error(403, "无权查看该用户信息");
        }
        return Result.success(userService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @RequestBody User user, HttpServletRequest request) {
        User currentUser = (User) request.getAttribute("currentUser");
        if (currentUser == null) {
            return Result.error(401, "未登录");
        }
        if (!currentUser.getRole().equals("admin") && !currentUser.getId().equals(id)) {
            return Result.error(403, "无权修改该用户信息");
        }
        User existing = userService.getById(id);
        if (existing == null) {
            return Result.error(404, "用户不存在");
        }
        return Result.success(userService.updateProfile(id, user));
    }

    @PutMapping("/{id}/role")
    public Result<User> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest request) {
        User currentUser = requireAdmin(request);
        if (currentUser.getId().equals(id)) return Result.error(400, "不能修改当前管理员自己的角色");
        userService.updateRole(id, body.get("role"));
        return Result.success(userService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        User currentUser = requireAdmin(request);
        userService.deleteUser(id);
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

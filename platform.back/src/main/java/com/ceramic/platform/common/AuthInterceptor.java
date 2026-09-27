package com.ceramic.platform.common;

import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final UserCache userCache;

    public AuthInterceptor(UserService userService, JwtUtil jwtUtil, ObjectMapper objectMapper, UserCache userCache) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        this.userCache = userCache;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        String method = request.getMethod().toUpperCase();

        if (isPublicApi(requestUri, method)) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            sendError(response, 401, token == null ? "未登录" : "登录已过期,请重新登录");
            return false;
        }

        Claims claims = jwtUtil.parseToken(token);
        Long userId = Long.parseLong(claims.getSubject());
        String role = claims.get("role", String.class);

        User user = userCache.get(userId, userService::getById);
        if (user == null) {
            sendError(response, 401, "用户不存在");
            return false;
        }

        Role r = Role.fromValue(role);
        if (!checkPermission(requestUri, method, r)) {
            sendError(response, 403, "无权访问");
            return false;
        }

        request.setAttribute("currentUser", user);
        request.setAttribute("currentUserId", userId);
        request.setAttribute("currentUserRole", role);
        return true;
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isPublicApi(String uri, String method) {
        if (uri.startsWith("/api/users/login") ||
            uri.startsWith("/api/users/service/login") ||
            uri.startsWith("/api/users/register") ||
            uri.startsWith("/api/users/sms/") ||
            uri.startsWith("/api/categories") ||
            uri.startsWith("/api/images/") ||
            uri.startsWith("/uploads/")) {
            return true;
        }
        // 商品列表/详情对消费者公开；但管理端 /api/products/admin 需鉴权(要注入 currentUser 做管理员校验)
        if (uri.startsWith("/api/products") && "GET".equals(method) && !uri.startsWith("/api/products/admin")) {
            return true;
        }
        if (uri.equals("/api/reviews") && "GET".equals(method)) {
            return true;
        }
        if ((uri.equals("/api/recommendations") ||
                uri.equals("/api/recommendations/generate") ||
                uri.matches("/api/recommendations/similar/\\d+")) && "GET".equals(method)) {
            return true;
        }
        // 秒杀活动列表/详情公开浏览（与商品一致）；抢购与查单仍需登录
        if (uri.startsWith("/api/seckill/activities") && "GET".equals(method)) {
            return true;
        }
        return false;
    }

    private boolean checkPermission(String uri, String method, Role role) {
        if (role.isAdmin()) {
            return true;
        }
        if (role.isService()) {
            return checkServicePermission(uri, method);
        }
        return checkCustomerPermission(uri, method);
    }

    private boolean checkCustomerPermission(String uri, String method) {
        if (uri.startsWith("/api/cart")) {
            return true;
        }
        if (uri.startsWith("/api/files")) {
            if ("POST".equals(method) && uri.matches("/api/files/upload")) {
                return true;
            }
            return false;
        }
        if (uri.startsWith("/api/addresses")) {
            return true;
        }
        if (uri.startsWith("/api/orders")) {
            if ("GET".equals(method)) return true;
            if ("POST".equals(method)) return true;
            if ("PUT".equals(method) && uri.matches("/api/orders/\\d+/status")) return true;
            if ("PUT".equals(method) && uri.matches("/api/orders/\\d+/address")) return true;
            if ("PATCH".equals(method) && uri.matches("/api/orders/\\d+/status")) return true;
            if ("DELETE".equals(method) && uri.matches("/api/orders/\\d+")) return true;
            return false;
        }
        if (uri.startsWith("/api/return-requests")) {
            if ("GET".equals(method)) return true;
            if ("POST".equals(method)) return true;
            if ("PUT".equals(method) && uri.matches("/api/return-requests/\\d+/ship-back")) return true;
            return false;
        }
        if (uri.startsWith("/api/reviews")) {
            if ("GET".equals(method)) return true;
            if ("POST".equals(method)) return true;
            return false;
        }
        if (uri.startsWith("/api/user-behaviors")) {
            if ("GET".equals(method)) return true;
            if ("POST".equals(method)) return true;
            return false;
        }
        if (uri.startsWith("/api/customizations")) {
            if ("GET".equals(method)) return true;
            if ("POST".equals(method)) return true;
            if ("PUT".equals(method) && uri.matches("/api/customizations/\\d+")) return true;
            return false;
        }
        if (uri.startsWith("/api/payments")) {
            // 顾客可查自己的流水、发起支付、收银台确认支付；网关回调/退款不放行（回调仅 admin 重推）
            if ("GET".equals(method)) return true;
            if ("POST".equals(method) && uri.equals("/api/payments")) return true;
            if ("POST".equals(method) && uri.matches("/api/payments/[^/]+/confirm")) return true;
            return false;
        }
        if (uri.startsWith("/api/notifications")) {
            if ("GET".equals(method)) return true;
            if ("PUT".equals(method) && uri.matches("/api/notifications/\\d+/read")) return true;
            if ("PUT".equals(method) && uri.equals("/api/notifications/read-all")) return true;
            return false;
        }
        if (uri.startsWith("/api/shop-settings")) {
            // 顾客可读取寄件方/退货地址用于展示；修改仅 admin
            if ("GET".equals(method)) return true;
            return false;
        }
        if (uri.startsWith("/api/seckill")) {
            if ("GET".equals(method)) return true;
            if ("POST".equals(method) && uri.matches("/api/seckill/\\d+/buy")) return true;
            return false;
        }
        if (uri.startsWith("/api/support-tickets")) {
            if ("GET".equals(method)) return true;
            if ("POST".equals(method)) return true;
            return false;
        }
        if (uri.startsWith("/api/chat")) {
            if (uri.startsWith("/api/chat/service")) return false;
            return true;
        }
        if (uri.startsWith("/api/users")) {
            if (uri.equals("/api/users/profile")) return true;
            return false;
        }
        return false;
    }

    private boolean checkServicePermission(String uri, String method) {
        if (uri.startsWith("/api/chat")) return true;
        if (uri.startsWith("/api/support-tickets")) return true;
        if (uri.startsWith("/api/return-requests")) {
            if ("GET".equals(method)) return true;
            if ("PUT".equals(method) && uri.matches("/api/return-requests/\\d+.*")) return true;
            return false;
        }
        if (uri.startsWith("/api/orders")) {
            if ("GET".equals(method)) return true;
            if ("POST".equals(method) && uri.matches("/api/orders/\\d+/ship")) return true;
            if ("PUT".equals(method) && uri.matches("/api/orders/\\d+/address")) return true;
            if ("PATCH".equals(method) && uri.matches("/api/orders/\\d+/status")) return true;
            return false;
        }
        if (uri.startsWith("/api/users")) {
            if (uri.equals("/api/users/customers")) return true;
            if (uri.equals("/api/users/service")) return true;
            if ("GET".equals(method) && uri.matches("/api/users/\\d+")) return true;
            return false;
        }
        if (uri.startsWith("/api/products")) {
            if ("GET".equals(method)) return true;
            return false;
        }
        if (uri.startsWith("/api/categories")) {
            if ("GET".equals(method)) return true;
            return false;
        }
        if (uri.startsWith("/api/admin")) return false;
        if (uri.startsWith("/api/cart")) return false;
        if (uri.startsWith("/api/customizations")) {
            // 客服工作台需查看客户定制单：仅放行只读
            if ("GET".equals(method)) return true;
            return false;
        }
        if (uri.startsWith("/api/payments")) {
            // 客服/管理员可查询流水与退款
            if ("GET".equals(method)) return true;
            if ("POST".equals(method) && uri.matches("/api/payments/[^/]+/refund")) return true;
            return false;
        }
        if (uri.startsWith("/api/notifications")) {
            if ("GET".equals(method)) return true;
            if ("PUT".equals(method) && uri.matches("/api/notifications/\\d+/read")) return true;
            if ("PUT".equals(method) && uri.equals("/api/notifications/read-all")) return true;
            return false;
        }
        if (uri.startsWith("/api/shop-settings")) {
            if ("GET".equals(method)) return true;
            return false;
        }
        if (uri.startsWith("/api/user-behaviors")) {
            if ("GET".equals(method)) return true;
            return false;
        }
        if (uri.startsWith("/api/reviews")) {
            if ("GET".equals(method)) return true;
            return false;
        }
        if (uri.startsWith("/api/files")) {
            if ("POST".equals(method) && uri.matches("/api/files/upload")) return true;
            return false;
        }
        return false;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> result = new HashMap<>();
        result.put("code", status);
        result.put("msg", message);
        result.put("data", null);
        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(result));
        writer.flush();
        writer.close();
    }
}

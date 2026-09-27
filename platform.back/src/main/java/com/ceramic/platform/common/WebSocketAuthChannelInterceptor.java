package com.ceramic.platform.common;

import com.ceramic.platform.entity.User;
import com.ceramic.platform.service.ChatService;
import com.ceramic.platform.service.UserService;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket / STOMP 鉴权拦截器。
 *
 * <p>WebSocket 握手不经过 MVC 层的 {@link AuthInterceptor}，因此这里在 STOMP 帧层面
 * 复刻同样的 JWT 鉴权与"顾客只能访问自己会话、禁止访问客服频道"的规则。</p>
 *
 * <ul>
 *   <li><b>CONNECT</b>：校验 {@code Authorization: Bearer <token>}，绑定 {@link Principal}（name=userId），
 *       并把角色存入会话属性，供后续 SUBSCRIBE 授权与 {@code convertAndSendToUser} 使用。</li>
 *   <li><b>SUBSCRIBE</b>：
 *     <ul>
 *       <li>{@code /topic/conversation/{id}} —— 顾客须为会话归属者（复用 {@link ChatService#requireConversationAccess}）。</li>
 *       <li>{@code /topic/service.inbox} —— 仅客服/管理员可订阅。</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String ATTR_ROLE = "role";
    private static final String ATTR_USER_ID = "userId";

    private final JwtUtil jwtUtil;
    private final UserCache userCache;
    private final UserService userService;
    private final ChatService chatService;

    public WebSocketAuthChannelInterceptor(JwtUtil jwtUtil, UserCache userCache,
                                           UserService userService, @Lazy ChatService chatService) {
        this.jwtUtil = jwtUtil;
        this.userCache = userCache;
        this.userService = userService;
        this.chatService = chatService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor.getFirstNativeHeader("Authorization"));
        if (token == null || !jwtUtil.validateToken(token)) {
            throw new IllegalArgumentException("WebSocket 未授权：token 无效或缺失");
        }
        Long userId = jwtUtil.getUserIdFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);
        User user = userCache.get(userId, userService::getById);
        if (user == null) {
            throw new IllegalArgumentException("WebSocket 未授权：用户不存在");
        }
        accessor.setUser(new StompPrincipal(String.valueOf(userId)));
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs != null) {
            attrs.put(ATTR_USER_ID, userId);
            attrs.put(ATTR_ROLE, role);
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) return;

        Map<String, Object> attrs = accessor.getSessionAttributes();
        Long userId = attrs == null ? null : (Long) attrs.get(ATTR_USER_ID);
        String role = attrs == null ? null : (String) attrs.get(ATTR_ROLE);
        if (userId == null || role == null) {
            throw new IllegalArgumentException("WebSocket 未授权：会话未认证");
        }

        if (destination.startsWith("/topic/service.inbox")) {
            if (!"service".equals(role) && !"admin".equals(role)) {
                throw new IllegalArgumentException("无权订阅客服频道");
            }
            return;
        }

        if (destination.startsWith("/topic/conversation/")) {
            Long conversationId = parseConversationId(destination);
            if (conversationId == null) {
                throw new IllegalArgumentException("非法的会话频道");
            }
            // 顾客只能订阅自己的会话；客服/管理员放行（与 ChatService.requireConversationAccess 一致）
            chatService.requireConversationAccess(conversationId, userId, role);
        }
    }

    private Long parseConversationId(String destination) {
        String suffix = destination.substring("/topic/conversation/".length());
        int slash = suffix.indexOf('/');
        if (slash >= 0) suffix = suffix.substring(0, slash);
        try {
            return Long.parseLong(suffix);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolveToken(String header) {
        if (header == null) return null;
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }

    /** 轻量 Principal：name 即 userId，供 STOMP user 目的地路由。 */
    private record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}

package com.ceramic.platform.service;

import com.ceramic.platform.entity.ChatMessage;
import com.ceramic.platform.entity.Notification;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * WebSocket / STOMP 实时推送服务。
 *
 * <p>作为业务流程的旁路：只依赖 {@link SimpMessagingTemplate}（由 websocket starter 自动装配），
 * 不反向依赖 ChatService / NotificationService，避免形成 Bean 循环依赖。</p>
 *
 * <p>所有方法都吞掉异常——推送失败绝不能影响订单/聊天等主业务事务，
 * 与 {@code NotificationService.create} 的容错风格一致。前端本身也保留 HTTP 拉取兜底。</p>
 *
 * <p>目的地约定：</p>
 * <ul>
 *   <li>{@code /topic/conversation/{id}} —— 单个会话房间，顾客与客服双方订阅。</li>
 *   <li>{@code /topic/service.inbox} —— 客服广播频道，新消息/新会话/转人工时提醒所有在线客服刷新列表。</li>
 *   <li>{@code /user/queue/notifications} —— 每个用户私有的站内通知队列（按 Principal 隔离）。</li>
 * </ul>
 */
@Service
public class RealtimePushService {

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** 向会话房间推送一条聊天消息（顾客端/客服端订阅同一 topic）。 */
    public void pushChatMessage(Long conversationId, ChatMessage message) {
        if (conversationId == null || message == null) return;
        try {
            messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, message);
        } catch (Exception ignored) {
            // 静默失败：推送是旁路
        }
    }

    /** 向所有在线客服广播一个"收件箱有变更"事件（顾客发新消息 / 新会话 / 转人工）。 */
    public void pushServiceInbox(Object event) {
        try {
            messagingTemplate.convertAndSend("/topic/service.inbox", event == null ? "" : event);
        } catch (Exception ignored) {
            // 静默失败：推送是旁路
        }
    }

    /** 向指定用户推送一条站内通知（按登录身份隔离）。 */
    public void pushNotification(Long userId, Notification notification) {
        if (userId == null || notification == null) return;
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(userId), "/queue/notifications", notification);
        } catch (Exception ignored) {
            // 静默失败：推送是旁路
        }
    }
}

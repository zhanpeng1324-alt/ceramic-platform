package com.ceramic.platform.service;

import com.ceramic.platform.entity.Notification;
import com.ceramic.platform.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内通知服务。
 * create(...) 在各业务状态流转处被调用（append-only）；异常不外抛，避免影响主业务事务。
 */
@Service
public class NotificationService {

    private static final int DEFAULT_LIMIT = 50;

    private final NotificationMapper notificationMapper;
    private final RealtimePushService realtimePushService;

    public NotificationService(NotificationMapper notificationMapper, RealtimePushService realtimePushService) {
        this.notificationMapper = notificationMapper;
        this.realtimePushService = realtimePushService;
    }

    /**
     * 写入一条通知。作为业务流转的旁路，任何异常都被吞掉——
     * 通知投递失败绝不能回滚订单/定制等主业务。
     */
    public void create(Long userId, String type, String title, String content, String bizType, Long bizId) {
        if (userId == null) return;
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setBizType(bizType);
            n.setBizId(bizId);
            notificationMapper.insert(n);
            // 实时推送到该用户的私有通知队列（insert 已回填自增 id）
            realtimePushService.pushNotification(userId, n);
        } catch (Exception ignored) {
            // 静默失败：通知是旁路，不影响主流程
        }
    }

    public List<Notification> listByUser(Long userId) {
        return notificationMapper.findByUser(userId, DEFAULT_LIMIT);
    }

    public int unreadCount(Long userId) {
        return notificationMapper.unreadCount(userId);
    }

    public void markRead(Long id, Long userId) {
        notificationMapper.markRead(id, userId);
    }

    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId);
    }
}

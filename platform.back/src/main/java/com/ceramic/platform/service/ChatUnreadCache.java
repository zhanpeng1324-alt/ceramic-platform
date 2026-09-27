package com.ceramic.platform.service;

import com.ceramic.platform.mapper.ChatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 客服待办未读数的 Redis 计数器。
 *
 * 设计要点（面试可讲）：
 * 1. Redis 是读路径，MySQL 仍是数据源（source of truth）——缓存值丢失可随时回源重建；
 * 2. 读缓存未命中（key 不存在，如 Redis 刚重启）时惰性回源 MySQL 并回填；
 * 3. Redis 不可用时所有方法静默降级为直查 MySQL，系统功能不受影响，只是变慢。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatUnreadCache {

    private static final String KEY_PREFIX = "chat:unread:";

    private final StringRedisTemplate redis;
    private final ChatMapper chatMapper;

    private String key(Long conversationId) {
        return KEY_PREFIX + conversationId;
    }

    /** 顾客发新消息：待办数 +1 */
    public void increment(Long conversationId) {
        try {
            redis.opsForValue().increment(key(conversationId));
        } catch (Exception e) {
            log.warn("Redis 未读数自增失败，降级跳过: {}", e.getMessage());
        }
    }

    /** 客服/AI 回复：待办数清零（MySQL 同步清零由调用方负责） */
    public void reset(Long conversationId) {
        try {
            redis.opsForValue().set(key(conversationId), "0");
        } catch (Exception e) {
            log.warn("Redis 未读数清零失败，降级跳过: {}", e.getMessage());
        }
    }

    /**
     * 读待办数：优先 Redis；key 不存在则回源 MySQL 并回填缓存；
     * Redis 不可用时直接返回 MySQL 值。
     */
    public long get(Long conversationId, long dbFallback) {
        try {
            String v = redis.opsForValue().get(key(conversationId));
            if (v != null) {
                return Long.parseLong(v);
            }
            redis.opsForValue().set(key(conversationId), String.valueOf(dbFallback));
            return dbFallback;
        } catch (Exception e) {
            log.warn("Redis 未读数读取失败，降级直查 MySQL: {}", e.getMessage());
            return dbFallback;
        }
    }
}

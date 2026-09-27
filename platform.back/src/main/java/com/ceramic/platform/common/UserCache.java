package com.ceramic.platform.common;

import com.ceramic.platform.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 鉴权热路径上的用户短期缓存。
 *
 * <p>{@code AuthInterceptor} 原本每个受保护请求都会 {@code userService.getById()} 查一次库，
 * 只为确认用户仍存在并取出其信息；QPS 升高时这是最先出现的瓶颈。此处用一个带 TTL 的进程内
 * 缓存收敛这些重复查询，零外部依赖。</p>
 *
 * <p>为避免"改角色/封号/删号后旧信息在 TTL 内仍生效"，凡是变更用户的服务方法都会调用
 * {@link #evict(Long)} 主动失效；即便漏调，缓存也会在 TTL（默认 30s）后自愈。</p>
 */
@Component
public class UserCache {

    private record Entry(User user, long expireAt) {}

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();
    private final long ttlMs;
    private final int maxSize;

    public UserCache(
            @Value("${auth.user-cache-ttl-ms:30000}") long ttlMs,
            @Value("${auth.user-cache-max-size:10000}") int maxSize) {
        this.ttlMs = ttlMs;
        this.maxSize = maxSize;
    }

    /**
     * 命中且未过期则直接返回缓存；否则用 {@code loader} 回源并写入缓存。
     * 回源结果为 null（用户不存在）时不缓存，避免把"不存在"钉住。
     */
    public User get(Long id, Function<Long, User> loader) {
        long now = System.currentTimeMillis();
        Entry hit = cache.get(id);
        if (hit != null && hit.expireAt() > now) {
            return hit.user();
        }
        User loaded = loader.apply(id);
        if (loaded != null) {
            // 简单的容量兜底：超过上限直接清空，避免无界增长（单商户量级足够）。
            if (cache.size() >= maxSize) {
                cache.clear();
            }
            cache.put(id, new Entry(loaded, now + ttlMs));
        } else {
            cache.remove(id);
        }
        return loaded;
    }

    /** 用户信息变更（改资料/改角色/删除）后主动失效，保证下次请求读到最新数据。 */
    public void evict(Long id) {
        if (id != null) {
            cache.remove(id);
        }
    }
}

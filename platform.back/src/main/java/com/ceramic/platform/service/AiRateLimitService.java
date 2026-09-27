package com.ceramic.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiRateLimitService {
    private final ConcurrentHashMap<Long, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();
    private final int maxRequestsPerMinute;

    public AiRateLimitService(@Value("${deepseek.rate-limit-per-minute:20}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = Math.max(1, maxRequestsPerMinute);
    }

    public void checkRateLimit(Long userId) {
        if (userId == null) {
            return;
        }
        long now = Instant.now().toEpochMilli();
        long windowStart = now - 60_000L;

        Deque<Long> timestamps = requestTimestamps.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequestsPerMinute) {
                throw new IllegalArgumentException("发送过于频繁，请稍后再试（每分钟最多 " + maxRequestsPerMinute + " 条）");
            }
            timestamps.addLast(now);
        }
    }
}

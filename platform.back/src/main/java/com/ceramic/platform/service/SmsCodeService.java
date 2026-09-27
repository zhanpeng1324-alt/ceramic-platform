package com.ceramic.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码服务：
 * - 验证码存 Redis（5 分钟 TTL），校验通过立即删除防重放
 * - 防刷：同号 60 秒发送间隔 + 每日上限 10 条 + 连续 5 次校验失败作废重发（防爆破）
 * - 降级：Redis 不可用时验证码与限流自动落到本地内存，功能不中断（与项目整体降级设计一致）
 */
@Service
public class SmsCodeService {

    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final long SEND_INTERVAL_MS = 60_000;
    private static final int DAILY_LIMIT = 10;
    private static final int MAX_VERIFY_FAILS = 5;

    private final StringRedisTemplate redis;
    private final SmsSender smsSender;
    private final SecureRandom random = new SecureRandom();

    /** Redis 不可用时的本地兜底存储 */
    private final Map<String, LocalCode> localStore = new ConcurrentHashMap<>();

    public SmsCodeService(StringRedisTemplate redis, SmsSender smsSender) {
        this.redis = redis;
        this.smsSender = smsSender;
    }

    public Map<String, Object> send(String phone) {
        long now = System.currentTimeMillis();
        String dayKey = "sms:day:" + phone + ":" + java.time.LocalDate.now();
        String limitKey = "sms:limit:" + phone;
        String codeKey = "sms:code:" + phone;

        // 发送间隔限制（60 秒）
        if (isRedisAlive()) {
            Boolean first = redis.opsForValue().setIfAbsent(limitKey, "1", Duration.ofMillis(SEND_INTERVAL_MS));
            if (!Boolean.TRUE.equals(first)) {
                throw new IllegalArgumentException("发送太频繁，请 60 秒后再试");
            }
            Long count = redis.opsForValue().increment(dayKey);
            if (count != null) {
                if (count == 1) redis.expire(dayKey, Duration.ofDays(1));
                if (count > DAILY_LIMIT) {
                    redis.delete(limitKey);
                    throw new IllegalArgumentException("今日验证码发送次数已达上限，请明天再试");
                }
            }
        } else {
            LocalCode old = localStore.get(phone);
            if (old != null && now - old.lastSendAt < SEND_INTERVAL_MS) {
                throw new IllegalArgumentException("发送太频繁，请 60 秒后再试");
            }
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        boolean stored = false;
        if (isRedisAlive()) {
            try {
                redis.opsForValue().set(codeKey, code, CODE_TTL);
                redis.delete("sms:fails:" + phone);
                stored = true;
            } catch (Exception e) {
                log.debug("Redis 写验证码失败，降级本地存储", e);
            }
        }
        if (!stored) {
            localStore.put(phone, new LocalCode(code, now + CODE_TTL.toMillis(), now));
        }

        smsSender.send(phone, code);

        Map<String, Object> result = new HashMap<>();
        result.put("demoMode", smsSender.demoMode());
        // 演示模式回显验证码（生产环境下 SmsSender 为真实通道，此字段不再返回）
        if (smsSender.demoMode()) {
            result.put("code", code);
        }
        return result;
    }

    /** 校验验证码，通过后立即作废（防重放）；失败过多强制重发（防爆破） */
    public void verify(String phone, String code) {
        String codeKey = "sms:code:" + phone;
        String stored = null;
        if (isRedisAlive()) {
            stored = redis.opsForValue().get(codeKey);
        }
        long now = System.currentTimeMillis();
        if (stored == null) {
            LocalCode local = localStore.get(phone);
            if (local != null && local.expireAt > now) {
                stored = local.code;
            }
        }

        if (stored == null) {
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }

        if (!stored.equals(code)) {
            recordFail(phone);
            throw new IllegalArgumentException("验证码错误");
        }

        // 校验通过：立即作废 + 清理失败计数与本地兜底
        if (isRedisAlive()) {
            redis.delete(codeKey);
            redis.delete("sms:fails:" + phone);
        }
        localStore.remove(phone);
    }

    private void recordFail(String phone) {
        String failsKey = "sms:fails:" + phone;
        Long fails = null;
        if (isRedisAlive()) {
            fails = redis.opsForValue().increment(failsKey);
            if (fails != null && fails == 1) redis.expire(failsKey, CODE_TTL);
        }
        int count = fails == null ? 1 : fails.intValue();
        if (count >= MAX_VERIFY_FAILS) {
            // 连续错误过多：作废当前验证码，强制重新获取
            if (isRedisAlive()) redis.delete("sms:code:" + phone);
            localStore.remove(phone);
            if (isRedisAlive()) redis.delete(failsKey);
            throw new IllegalArgumentException("错误次数过多，请重新获取验证码");
        }
    }

    private boolean isRedisAlive() {
        try {
            redis.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static class LocalCode {
        final String code;
        final long expireAt;
        final long lastSendAt;

        LocalCode(String code, long expireAt, long lastSendAt) {
            this.code = code;
            this.expireAt = expireAt;
            this.lastSendAt = lastSendAt;
        }
    }
}

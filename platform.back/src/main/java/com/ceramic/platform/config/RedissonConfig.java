package com.ceramic.platform.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Redisson 客户端配置（惰性创建）。
 *
 * 不用官方 RedissonAutoConfigurationV2 的原因：它会在启动阶段强制连接 Redis，
 * Redis 不可用时整个应用启动失败。这里的 @Lazy Bean 只在第一次真正使用时才创建——
 * ProductCache 的调用点均有 try-catch 降级，因此：
 * - Redis 正常：分布式锁 + 布隆过滤器完整生效；
 * - Redis 挂了/没开 Docker：降级为无锁回源 + 仅空值缓存，应用照常启动和运行。
 */
@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6380}") int port) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                // 连接失败快速失败：避免使用线程长时间阻塞
                .setConnectTimeout(1000)
                .setTimeout(1000)
                .setRetryAttempts(0)
                .setRetryInterval(100);
        return Redisson.create(config);
    }
}

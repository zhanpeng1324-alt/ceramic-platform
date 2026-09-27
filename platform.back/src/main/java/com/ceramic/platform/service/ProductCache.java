package com.ceramic.platform.service;

import com.ceramic.platform.entity.Product;
import com.ceramic.platform.mapper.ProductMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 商品详情的 Redis 缓存（Cache-Aside + 缓存三防），面试重点：
 *
 * 1. 只缓存 Product 实体这种「人人相同的公共数据」，评论/销量/能否评价等实时数据不进缓存；
 * 2. 读：命中直接返回；未命中回源 MySQL 并回填；
 * 3. 写：管理员更新/删除/上下架时立刻删除对应缓存（evict），下次读取自然重建；
 * 4. Redis 不可用时静默降级为直查 MySQL。
 *
 * 缓存三防：
 * - 穿透（查询不存在的 id 打穿到库）：Redisson 布隆过滤器前置拦截 + 空值占位短缓存双保险；
 *   布隆初始化失败时退化为仅空值缓存；布隆无法移除已删商品，由空值缓存兜底；
 * - 击穿（热点 key 过期瞬间大量请求同时回源）：Redisson 分布式锁互斥重建，
 *   抢到锁的线程回源写缓存，其余线程等待后二次读缓存，仍失败再直查降级；
 * - 雪崩（大量 key 同时过期）：TTL 在基准 10 分钟上加 ±2 分钟随机抖动，错峰过期。
 */
@Slf4j
@Component
public class ProductCache {

    private static final String KEY_PREFIX = "product:detail:";
    private static final String LOCK_PREFIX = "lock:product:detail:";
    private static final String BLOOM_NAME = "bloom:product:ids";
    private static final String NULL_PLACEHOLDER = "__NULL__";
    private static final Duration BASE_TTL = Duration.ofMinutes(10);
    private static final Duration TTL_JITTER = Duration.ofMinutes(2);
    private static final Duration NULL_TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redis;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;
    private final RedissonClient redisson;
    private volatile RBloomFilter<Long> bloom;

    public ProductCache(StringRedisTemplate redis, ProductMapper productMapper,
                        ObjectMapper objectMapper, @Lazy RedissonClient redisson) {
        this.redis = redis;
        this.productMapper = productMapper;
        this.objectMapper = objectMapper;
        this.redisson = redisson;
    }

    /** 启动时预热布隆过滤器：加载全量商品 id。失败不阻断启动，仅退化为空值缓存防线。 */
    @PostConstruct
    public void initBloomFilter() {
        try {
            RBloomFilter<Long> filter = redisson.getBloomFilter(BLOOM_NAME);
            filter.tryInit(100_000L, 0.03);
            List<Long> ids = productMapper.findAllIds();
            for (Long id : ids) {
                filter.add(id);
            }
            bloom = filter;
            log.info("商品布隆过滤器预热完成：{} 个商品，预期容量 10 万，误判率 3%", ids.size());
        } catch (Exception e) {
            bloom = null;
            log.warn("布隆过滤器初始化失败，穿透防护退化为仅空值缓存: {}", e.getMessage());
        }
    }

    /** 新建商品后调用：把新 id 加入布隆（布隆只增不减，删除商品靠空值缓存兜底） */
    public void onProductCreated(Long id) {
        RBloomFilter<Long> filter = bloom;
        if (filter == null || id == null) {
            return;
        }
        try {
            filter.add(id);
        } catch (Exception e) {
            log.warn("布隆过滤器添加商品 id 失败（不影响下单，仅影响穿透拦截）: {}", e.getMessage());
        }
    }

    private String key(Long id) {
        return KEY_PREFIX + id;
    }

    /** 读商品：布隆拦截 → 缓存命中 → 分布式锁互斥重建 → 未抢到锁二次读缓存 → 直查降级 */
    public Product get(Long id) {
        if (id == null) {
            return null;
        }
        // 穿透防线 1：布隆过滤器，判定「一定不存在」的 id 直接拦截，不碰 Redis 和 MySQL
        RBloomFilter<Long> filter = bloom;
        if (filter != null && !containsQuietly(filter, id)) {
            return null;
        }
        String cacheKey = key(id);
        String json = readQuietly(cacheKey);
        if (json != null) {
            return parse(json);
        }
        // 击穿防线：热点 key 过期瞬间，用分布式锁保证只有一个线程回源重建
        RLock lock = tryGetLock(id);
        boolean locked = lock != null;
        if (locked) {
            try {
                locked = lock.tryLock(1, 10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                locked = false;
            } catch (Exception e) {
                log.warn("获取分布式锁失败，降级为无锁回源: {}", e.getMessage());
                locked = false;
            }
        }
        if (locked) {
            try {
                // 双重检查：排队等待期间缓存可能已被其它线程重建
                json = readQuietly(cacheKey);
                if (json != null) {
                    return parse(json);
                }
                Product product = productMapper.findById(id);
                if (product == null) {
                    // 穿透防线 2：空值占位短缓存，反复请求不存在的 id 也不会持续打库
                    putQuietly(cacheKey, NULL_PLACEHOLDER, NULL_TTL);
                    return null;
                }
                putQuietly(cacheKey, writeQuietly(product), randomTtl());
                return product;
            } finally {
                lock.unlock();
            }
        }
        // 未抢到锁：短暂等待后二次读缓存（大概率已被重建），仍无则直查降级
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        json = readQuietly(cacheKey);
        if (json != null) {
            return parse(json);
        }
        return productMapper.findById(id);
    }

    /** 写操作（更新/删除/上下架）后删除缓存，下次读取重建 */
    public void evict(Long id) {
        try {
            redis.delete(key(id));
        } catch (Exception e) {
            log.warn("Redis 商品缓存删除失败（TTL 兜底会自动过期）: {}", e.getMessage());
        }
    }

    /** Redisson 故障冷却：失败后 60 秒内视为不可用，避免每次请求都白等连接超时 */
    private volatile long redissonDownUntil = 0L;
    private static final long REDISSON_COOLDOWN_MS = 60_000L;

    /** Redisson 不可用（未启动等）时返回 null，调用方降级为无锁回源 */
    private RLock tryGetLock(Long id) {
        if (System.currentTimeMillis() < redissonDownUntil) {
            return null;
        }
        try {
            return redisson.getLock(LOCK_PREFIX + id);
        } catch (Exception e) {
            redissonDownUntil = System.currentTimeMillis() + REDISSON_COOLDOWN_MS;
            log.warn("Redisson 客户端不可用，{} 秒内降级为无锁回源: {}",
                    REDISSON_COOLDOWN_MS / 1000, e.getMessage());
            return null;
        }
    }

    /**
     * 布隆查询降级封装：运行期 Redis 断连时抛出的异常不能打断读取链路，
     * 返回 true（放行）让后续缓存/数据库兜底，宁可多查一次库也不给用户报错。
     */
    private boolean containsQuietly(RBloomFilter<Long> filter, Long id) {
        try {
            return filter.contains(id);
        } catch (Exception e) {
            log.warn("布隆过滤器查询失败，放行走缓存/数据库兜底: {}", e.getMessage());
            return true;
        }
    }

    /** 雪崩防线：基准 TTL + 随机抖动，避免同批 key 同时过期 */
    private Duration randomTtl() {
        long jitterMs = TTL_JITTER.toMillis();
        return BASE_TTL.plusMillis(ThreadLocalRandom.current().nextLong(-jitterMs, jitterMs));
    }

    private Product parse(String json) {
        if (NULL_PLACEHOLDER.equals(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Product.class);
        } catch (Exception e) {
            log.warn("缓存商品反序列化失败，当作未命中处理: {}", e.getMessage());
            return null;
        }
    }

    private String readQuietly(String cacheKey) {
        try {
            return redis.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis 商品缓存读取失败，降级直查 MySQL: {}", e.getMessage());
            return null;
        }
    }

    private void putQuietly(String cacheKey, String json, Duration ttl) {
        try {
            redis.opsForValue().set(cacheKey, json, ttl);
        } catch (Exception e) {
            log.warn("Redis 商品缓存写入失败，降级跳过: {}", e.getMessage());
        }
    }

    private String writeQuietly(Product product) {
        try {
            return objectMapper.writeValueAsString(product);
        } catch (Exception e) {
            return null;
        }
    }
}

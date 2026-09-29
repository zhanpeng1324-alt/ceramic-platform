package com.ceramic.platform.service;

import com.ceramic.platform.config.RabbitMQConfig;
import com.ceramic.platform.entity.Order;
import com.ceramic.platform.entity.OrderItem;
import com.ceramic.platform.entity.Product;
import com.ceramic.platform.entity.SeckillActivity;
import com.ceramic.platform.entity.SeckillOrder;
import com.ceramic.platform.mapper.OrderMapper;
import com.ceramic.platform.mapper.ProductMapper;
import com.ceramic.platform.mapper.SeckillActivityMapper;
import com.ceramic.platform.mapper.SeckillOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 秒杀核心服务。
 *
 * 高并发四层链路：
 *  ① 接口限流：单用户每秒最多 1 次抢购（Redis INCR + 1s 过期）；
 *  ② Redis Lua 原子扣减：防重（SISMEMBER）→ 判库存 → DECR + SADD 三步原子执行，物理上杜绝超卖与重复购；
 *  ③ MQ 异步落库：扣减成功仅发消息，MySQL 写入由消费者排队完成（削峰填谷）；
 *  ④ 兜底：MQ 不可用时降级为 DB CAS 同步扣减；超时未付经延迟队列取消并逐级回补库存。
 *
 * 展示状态按当前时间实时计算，与 DB status 解耦，避免依赖定时任务翻转状态。
 */
@Service
public class SeckillService {

    private static final Logger log = LoggerFactory.getLogger(SeckillService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 秒杀单支付时限（毫秒）：超时由延迟队列取消并回补库存 */
    private static final long PAY_TIMEOUT_MS = 30 * 60 * 1000L;

    /** Lua 返回码 */
    public static final long LUA_OK = 0L;
    public static final long LUA_DUPLICATE = 1L;
    public static final long LUA_SOLD_OUT = 2L;

    /**
     * 原子抢购脚本：同一活动同一用户仅可成功一次。
     * KEYS[1]=库存串 KEYS[2]=已购集合 ARGV[1]=userId
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>("" +
            "if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return 1 end " +
            "local stock = tonumber(redis.call('GET', KEYS[1]) or '-1') " +
            "if stock == nil or stock <= 0 then return 2 end " +
            "redis.call('DECR', KEYS[1]) " +
            "redis.call('SADD', KEYS[2], ARGV[1]) " +
            "return 0", Long.class);

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;
    private final OrderMapper normalOrderMapper;
    private final ProductMapper productMapper;
    private final NotificationService notificationService;
    private final StringRedisTemplate redis;
    private final RabbitTemplate rabbitTemplate;

    public SeckillService(SeckillActivityMapper activityMapper, SeckillOrderMapper orderMapper,
                          OrderMapper normalOrderMapper, ProductMapper productMapper,
                          NotificationService notificationService,
                          StringRedisTemplate redis, RabbitTemplate rabbitTemplate) {
        this.activityMapper = activityMapper;
        this.orderMapper = orderMapper;
        this.normalOrderMapper = normalOrderMapper;
        this.productMapper = productMapper;
        this.notificationService = notificationService;
        this.redis = redis;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ==================== 用户端 ====================

    /** 全部活动 + 实时展示状态与抢购进度 */
    public List<SeckillActivity> listActivities() {
        List<SeckillActivity> list = activityMapper.findAll();
        for (SeckillActivity a : list) {
            applyDisplayState(a);
        }
        return list;
    }

    public SeckillActivity detail(Long id) {
        SeckillActivity a = activityMapper.findById(id);
        if (a != null) {
            applyDisplayState(a);
        }
        return a;
    }

    /**
     * 抢购入口。成功后订单异步落库，返回流水号供前端轮询结果。
     */
    public String buy(Long userId, Long activityId) {
        SeckillActivity a = requireBuyable(activityId);

        // ① 单用户限流：1 秒 1 次；Redis 不可用时跳过限流（降级到 DB 唯一索引防重）
        try {
            String rateKey = "seckill:rate:" + userId;
            Boolean first = redis.opsForValue().setIfAbsent(rateKey, "1", Duration.ofSeconds(1));
            if (!Boolean.TRUE.equals(first)) {
                throw new IllegalArgumentException("操作太频繁，稍后再试");
            }
        } catch (Exception ex) {
            log.warn("Redis 限流不可用，跳过限流检查：activity={} user={}", activityId, userId, ex);
        }

        // ② Lua 原子：防重 + 判库存 + 扣减
        Long r;
        try {
            r = redis.execute(SECKILL_SCRIPT,
                    List.of(stockKey(activityId), boughtKey(activityId)), String.valueOf(userId));
        } catch (Exception e) {
            // ③ 降级：Redis 不可用时走 DB CAS 同步扣减（唯一索引仍保证一人一单）
            log.warn("Redis 扣减失败，降级 DB CAS：activity={}", activityId, e);
            return buyViaDb(userId, a);
        }
        if (r == null) r = LUA_SOLD_OUT;
        if (r == LUA_DUPLICATE) {
            throw new IllegalArgumentException("您已抢购过该活动，每人限购一件");
        }
        if (r == LUA_SOLD_OUT) {
            throw new IllegalArgumentException("已被抢光啦，下次趁早");
        }
        return enqueueOrder(userId, a);
    }

    /** 我的秒杀单 */
    public List<SeckillOrder> myOrders(Long userId) {
        return orderMapper.findByUserId(userId);
    }

    /** 按流水号查单（前端抢购后轮询落库结果）；归属校验防越权 */
    public SeckillOrder orderDetail(Long userId, String orderNo) {
        SeckillOrder o = orderMapper.findWithProductByOrderNo(orderNo);
        if (o == null || !userId.equals(o.getUserId())) {
            throw new IllegalArgumentException("订单不存在");
        }
        return o;
    }

    // ==================== 管理端 ====================

    public SeckillActivity create(Long productId, BigDecimal seckillPrice, int totalStock,
                                  String startTime, String endTime) {
        if (seckillPrice == null || seckillPrice.signum() <= 0) {
            throw new IllegalArgumentException("秒杀价必须大于 0");
        }
        if (totalStock <= 0) {
            throw new IllegalArgumentException("秒杀库存必须大于 0");
        }
        LocalDateTime s = LocalDateTime.parse(startTime.replace(' ', 'T'));
        LocalDateTime e = LocalDateTime.parse(endTime.replace(' ', 'T'));
        if (!e.isAfter(s)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        SeckillActivity a = new SeckillActivity();
        a.setProductId(productId);
        a.setSeckillPrice(seckillPrice);
        a.setTotalStock(totalStock);
        a.setAvailableStock(totalStock);
        a.setStartTime(s.format(FMT));
        a.setEndTime(e.format(FMT));
        a.setStatus(LocalDateTime.now().isBefore(s) ? "PENDING" : "ACTIVE");
        activityMapper.insert(a);
        // 库存预热进 Redis，TTL 覆盖活动全程 + 缓冲；Redis 不可用时仅记日志，降级路径（DB CAS）仍可用
        long ttlSec = Duration.between(LocalDateTime.now(),
                e.plus(Duration.ofHours(24))).getSeconds();
        try {
            redis.opsForValue().set(stockKey(a.getId()), String.valueOf(totalStock),
                    Duration.ofSeconds(Math.max(ttlSec, 3600)));
            redis.delete(boughtKey(a.getId()));
        } catch (Exception ex) {
            log.warn("Redis 库存预热失败，秒杀活动降级 DB 路径：activity={}", a.getId(), ex);
        }
        log.info("秒杀活动已创建并预热库存：id={} productId={} stock={}", a.getId(), productId, totalStock);
        return detail(a.getId());
    }

    /** 下线活动：未开始（作废）或进行中（提前下架）均可；已结束/已下线拒绝。 */
    public void offline(Long id) {
        SeckillActivity a = activityMapper.findById(id);
        if (a == null) throw new IllegalArgumentException("活动不存在");
        String display = displayStatus(a);
        if ("CANCELLED".equals(display)) {
            throw new IllegalArgumentException("活动已下线，请勿重复操作");
        }
        if ("ENDED".equals(display)) {
            throw new IllegalArgumentException("活动已自然结束，无需下线");
        }
        activityMapper.updateStatusGuarded(id, "CANCELLED", a.getStatus());
        // 清 Redis 库存与购买资格：Lua 扣减链路立即不可达，停止售卖
        try {
            redis.delete(stockKey(id));
            redis.delete(boughtKey(id));
        } catch (Exception ex) {
            log.warn("Redis 库存清理失败（活动已下线，DB 侧状态已更新）：activity={}", id, ex);
        }
        log.info("秒杀活动已下线（原状态：{}）：activity={}", display, id);
    }

    // ==================== 内部：核心链路 ====================

    /** 扣减成功 → 发 MQ 异步落库；MQ 不可用降级同步落库 */
    private String enqueueOrder(Long userId, SeckillActivity a) {
        String orderNo = genNo("SK");
        String payload = orderNo + "|" + a.getId() + "|" + userId + "|" + a.getProductId()
                + "|" + a.getSeckillPrice().toPlainString();
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_CREATE_EXCHANGE,
                    RabbitMQConfig.SECKILL_CREATE_ROUTING_KEY, payload);
            log.info("抢购成功，订单排队落库：orderNo={} activity={} user={}", orderNo, a.getId(), userId);
        } catch (Exception e) {
            log.warn("MQ 不可用，秒杀订单同步落库：orderNo={}", orderNo, e);
            persistOrder(payload);
        }
        return orderNo;
    }

    /** 消费者与降级路径共用的落库逻辑；失败时回补 Redis，保证「扣了库存必有订单」 */
    public void persistOrder(String payload) {
        String[] parts = payload.split("\\|");
        SeckillOrder o = new SeckillOrder();
        o.setOrderNo(parts[0]);
        o.setActivityId(Long.parseLong(parts[1]));
        o.setUserId(Long.parseLong(parts[2]));
        o.setProductId(Long.parseLong(parts[3]));
        o.setQuantity(1);
        o.setSeckillPrice(new BigDecimal(parts[4]));
        o.setPayAmount(new BigDecimal(parts[4]));
        o.setStatus("PENDING");
        // 下线兜底：抢购请求在下线前一刻通过 Lua，MQ 消息仍在途——落库前复检活动状态，
        // 已下线则直接丢弃订单；Redis 库存/资格键已随下线删除，无需也无法回补
        SeckillActivity act = activityMapper.findById(o.getActivityId());
        if (act == null || "CANCELLED".equals(act.getStatus())) {
            log.info("活动已下线，丢弃在途秒杀订单：orderNo={} activity={}", parts[0], o.getActivityId());
            return;
        }
        try {
            orderMapper.insert(o);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 唯一索引兜底命中：MQ 重复投递。扣减发生在首次，回补本次重复消息造成的多余占用不存在——
            // 重复投递不伴随新的扣减，直接忽略即可。
            log.info("秒杀订单重复投递，忽略：orderNo={}", parts[0]);
            return;
        } catch (Exception e) {
            // 落库失败：回补 Redis 库存与资格，用户可重试，杜绝「扣了库存却没订单」
            log.error("秒杀订单落库失败，回补库存：orderNo={}", parts[0], e);
            restockRedis(o.getActivityId(), o.getUserId());
            throw new IllegalStateException("秒杀订单落库失败", e);
        }
        // 30 分钟未支付自动取消（延迟队列 → 死信）。发送失败不回补：订单已落库，
        // 此时回补会造成「订单在库、库存却回来了」的超卖；对账任务（第二期）兜底取消
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_DELAY_QUEUE, parts[0], m -> {
                m.getMessageProperties().setExpiration(String.valueOf(PAY_TIMEOUT_MS));
                return m;
            });
        } catch (Exception e) {
            log.warn("秒杀超时消息发送失败，等待对账兜底取消：orderNo={}", parts[0], e);
        }
        // DB 影子扣减：与 buyViaDb 路径对齐，保证「每个落库订单恰对应一次 DB 扣减」，
        // 超时取消的 DB 回补才能守恒（否则 Redis 路径订单超时回补会凭空 +1）。0 行说明
        // Redis 与 DB 库存出现分叉，以 Redis 为准，仅记日志不阻断
        if (activityMapper.deductStock(o.getActivityId()) == 0) {
            log.warn("DB 库存影子扣减为0，Redis 与 DB 库存分叉（以 Redis 为准）：orderNo={} activity={}",
                    parts[0], o.getActivityId());
        }
    }

    /**
     * 秒杀支付成功 → 转正式订单，履约（补地址/发货/物流/收货/售后）统一走订单域。
     * 与支付 CAS 同事务：转换失败整体回滚，秒杀单回到 PENDING 可重新支付，天然幂等。
     * 地址留空由用户在订单页补填。
     */
    @Transactional
    public void convertToOrder(SeckillOrder so) {
        Order order = new Order();
        order.setOrderNo("CO" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        order.setUserId(so.getUserId());
        order.setTotalAmount(so.getPayAmount());
        order.setStatus("PAID");
        order.setPayType("seckill");
        order.setPayTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        order.setRemark("秒杀活动#" + so.getActivityId() + " 抢购（秒杀单 " + so.getOrderNo() + "），请补填收货地址");
        normalOrderMapper.insert(order);
        Product p = productMapper.findById(so.getProductId());
        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setProductId(so.getProductId());
        item.setProductName(p != null ? p.getName() : "秒杀商品#" + so.getProductId());
        item.setImageUrl(p != null ? p.getImageUrl() : null);
        item.setUnitPrice(so.getSeckillPrice());
        item.setQuantity(so.getQuantity());
        item.setSubtotal(so.getPayAmount());
        normalOrderMapper.insertItem(item);
        // 回填正式订单号，建立秒杀单 → 正式订单关联（售后退款联动同步秒杀单状态用）
        orderMapper.markConverted(so.getId(), order.getOrderNo());
        notificationService.create(so.getUserId(), "ORDER", "秒杀抢购成功",
                "您的秒杀订单 " + order.getOrderNo() + " 已支付成功，请到【订单】页补填收货地址，商家将尽快发货。",
                "ORDER", order.getId());
        log.info("秒杀单已转正式订单：seckill={} order={} user={}", so.getOrderNo(), order.getOrderNo(), so.getUserId());
    }

    /** Redis 不可用降级：DB CAS 扣减 + 同步落库 + 唯一索引防重 */
    private String buyViaDb(Long userId, SeckillActivity a) {
        int rows = activityMapper.deductStock(a.getId());
        if (rows == 0) {
            throw new IllegalArgumentException("已被抢光啦，下次趁早");
        }
        String orderNo = genNo("SK");
        SeckillOrder o = new SeckillOrder();
        o.setOrderNo(orderNo);
        o.setActivityId(a.getId());
        o.setUserId(userId);
        o.setProductId(a.getProductId());
        o.setQuantity(1);
        o.setSeckillPrice(a.getSeckillPrice());
        o.setPayAmount(a.getSeckillPrice());
        o.setStatus("PENDING");
        try {
            orderMapper.insert(o);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            activityMapper.restock(a.getId(), 1);
            throw new IllegalArgumentException("您已抢购过该活动，每人限购一件");
        } catch (Exception e) {
            activityMapper.restock(a.getId(), 1);
            throw new IllegalStateException("下单失败，请重试", e);
        }
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_DELAY_QUEUE, orderNo, m -> {
                m.getMessageProperties().setExpiration(String.valueOf(PAY_TIMEOUT_MS));
                return m;
            });
        } catch (Exception ignore) {
            // 超时消息发送失败不影响订单有效性；DB 侧对账任务可兜底（第二期）
        }
        return orderNo;
    }

    /** 超时/取消回补：Redis 库存 +1、移除已购资格（DB 回补由调用方按需执行） */
    public void restockRedis(Long activityId, Long userId) {
        try {
            redis.opsForValue().increment(stockKey(activityId));
            redis.opsForSet().remove(boughtKey(activityId), String.valueOf(userId));
        } catch (Exception e) {
            log.warn("Redis 回补失败（库存以 DB 为准做对账）：activity={} user={}", activityId, userId, e);
        }
    }

    // ==================== 辅助 ====================

    private SeckillActivity requireBuyable(Long activityId) {
        SeckillActivity a = activityMapper.findById(activityId);
        if (a == null) throw new IllegalArgumentException("活动不存在");
        String st = displayStatus(a);
        return switch (st) {
            case "PENDING" -> throw new IllegalArgumentException("活动尚未开始");
            case "ENDED" -> throw new IllegalArgumentException("活动已结束");
            case "CANCELLED" -> throw new IllegalArgumentException("活动已作废");
            default -> a;
        };
    }

    /** 展示状态按当前时间实时计算，不依赖定时任务 */
    public String displayStatus(SeckillActivity a) {
        String now = LocalDateTime.now().format(FMT);
        if ("CANCELLED".equals(a.getStatus())) return "CANCELLED";
        if (now.compareTo(a.getStartTime()) < 0) return "PENDING";
        if (now.compareTo(a.getEndTime()) >= 0) return "ENDED";
        return "ACTIVE";
    }

    private void applyDisplayState(SeckillActivity a) {
        a.setStatus(displayStatus(a));
        // 进度实时性：Redis 路径下 DB 库存不即时扣减（扣在预热库存上），用 Redis 值覆盖展示；
        // Redis 不可用时保留 DB 值（降级路径会同步扣 DB），不影响页面可用性。
        try {
            String s = redis.opsForValue().get(stockKey(a.getId()));
            if (s != null) {
                a.setAvailableStock((int) Math.min(Long.parseLong(s), a.getTotalStock()));
            }
        } catch (Exception ignore) {
            // 降级展示 DB 库存
        }
    }

    public static String stockKey(Long activityId) {
        return "seckill:stock:" + activityId;
    }

    public static String boughtKey(Long activityId) {
        return "seckill:bought:" + activityId;
    }

    private static String genNo(String prefix) {
        return prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ThreadLocalRandom.current().nextInt(10000, 99999);
    }
}

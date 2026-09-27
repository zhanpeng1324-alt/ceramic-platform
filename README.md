# Ceramic Platform — 陶瓷定制电商全栈平台

基于 Spring Boot 3 + Vue 3 的单商户陶瓷电商系统，覆盖 商品/购物车/订单/支付/售后/定制/秒杀/客服 全业务闭环，重点打磨**高并发秒杀链路、缓存高可用与支付一致性**。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.2、MyBatis、MySQL 8.0、Redis 7（Redisson）、RabbitMQ 3.13 |
| 前端 | Vue 3 + TypeScript + Pinia + Vite |
| 工程化 | 多阶段 Dockerfile、docker compose、JWT 认证、管理端审计日志 |

## 核心设计

### 秒杀链路（Redis + Lua + MQ）
- **Lua 脚本原子三步**：`SISMEMBER` 防重复购买 → 判断库存 → `DECR` 扣减 + `SADD` 登记已购，物理上杜绝超卖与一人多单
- **削峰**：抢购成功仅投递 RabbitMQ，订单由消费者异步落库；30 分钟 TTL 延迟消息 → 死信队列超时未支付自动取消并**双回补**（Redis 库存/资格 + DB）
- **在途消息兜底**：活动下线时对已发出的延迟消息做丢弃防护，防止停售后仍产生订单
- **降级**：Redis 不可用时自动降级为数据库乐观锁（`UPDATE ... WHERE stock > 0`）CAS 扣减，唯一索引兜底一人一单
- **退款联动**：已支付订单退款/管理员取消时，同步秒杀单状态（不回补资格，防止"退款再抢"）

### 缓存高可用三防
- **防穿透**：Redisson 布隆过滤器（预热全量 id + 新品钩子）+ 空值占位；断连自动降级放行
- **防击穿**：分布式锁互斥重建 + 双重检查
- **防雪崩**：TTL 随机抖动（600s ± 120s）；查询失败 60s 冷却期直查 DB，保证"Redis 挂了应用照常跑"

### 支付与数据一致性
- 统一收银台组件承接 订单/秒杀/定制定金/定制尾款 四类支付
- CAS 状态守卫更新（`updateStatusGuarded`）防止并发状态跳变；支付幂等校验；退款金额封顶（实付 - 累计已退）
- 管理端敏感操作全量审计日志（audit_logs）

### 权限
RBAC（admin/customer/service 三角色）+ 逐接口资源归属校验 + 管理端双重权限（拦截器 + 控制器层复查）

## 压测数据（单机：i7-12700H / 15.7G / MySQL+Redis 同机 / JMeter）

| 场景 | 结果 |
|---|---|
| 商品详情 200 并发，缓存开启 | **2,761 QPS，P99 130ms**，16 万+ 请求零失败 |
| 同接口关闭缓存（对照） | 67 QPS，P99 2,211ms |
| 秒杀 1,000 用户瞬时抢购（库存 200） | 成功订单恰 200，**零超卖、零重复**，约 670 QPS |
| 恶意 id 防穿透（43 万次请求） | 布隆全拦，压测期间 `Innodb_rows_read` 增量为 0，7,207 QPS |

> 数字为单机保守值，结论以开关缓存/开关防护的相对对比为准。

## 快速启动

```bash
# 0) 前置：JDK 17+、Node 18+、Docker、Maven(wrapper 自带)
# 1) 启动中间件（MySQL 可用本机或容器）
cd platform.back && docker compose up -d rabbitmq redis
# 2) 初始化数据库（schema + 全量迁移脚本，按文件名顺序执行）
mysql -uroot -p ceramic < platform.back/src/main/resources/db/schema.sql
bash platform.back/src/main/resources/db/apply_all.sh   # 或按 migration/ 目录手动执行
# 3) 后端（默认 dev profile，中间件不可用会自动降级直查 DB，应用照常启动）
./mvnw spring-boot:run
# 4) 前端
cd ceramic.ui && npm install && npm run dev   # http://localhost:5173
```

**默认演示账号**（演示短信登录，验证码回显在前端提示中）：`13800000000`（管理员）、任意 `139xxxxxxxx` 手机号自动注册为顾客。

## 生产部署

- 使用 `prod` profile（`application-prod.properties`），所有敏感项**强制环境变量**注入，缺失即启动失败，示例见 [.env.example](platform.back/.env.example)：
  `DB_URL / DB_USERNAME / DB_PASSWORD / JWT_SECRET / DEEPSEEK_API_KEY / SMS_DEMO_MODE=false`
- 短信验证码回显仅限本地开发（`sms.demo-mode=true`）；生产必须接入真实短信通道并将 `SMS_DEMO_MODE` 置 `false`，否则发码接口直接报错拒绝
- docker compose 中间件密码均已参数化（`${DB_PASSWORD:-默认演示值}`），生产请务必覆盖

## 目录结构

```
platform.back   # Spring Boot 后端（seckill/mq/cache/config 按域分包）
  ├─ src/main/resources/db   # 建表与迁移脚本
  ├─ perf-test → ../perf-test # JMeter 压测脚本（缓存对比/防超卖/布隆拦截）
ceramic.ui      # Vue 3 前端（views/product|seckill|customize|admin 分域）
perf-test       # JMeter 场景脚本 + Node 回归脚本（regression-test*.mjs 可重复执行）
```

## 测试与验证

- **回归脚本**：`node perf-test/regression-test.mjs`（核心交易链 17 项）+ `regression-test-2.mjs`（订单流转/定制链/周边 12 项），前后端启动即可跑
- **压测**：JMeter 打开 `perf-test/SeckillPerfTest.jmx`，三个线程组对应上表三个场景

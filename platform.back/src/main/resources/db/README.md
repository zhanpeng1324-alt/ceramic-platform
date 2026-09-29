# ceramic 数据库脚本说明

本目录管理 ceramic（单商家陶瓷电商）数据库的结构脚本。项目未接入 Flyway，
所有脚本均为**手工执行**（Navicat 或命令行 `mysql`）。

## 目录结构

- `schema.sql` —— **全量合并基线结构**，面向【全新的空数据库】，一次建全所有表。
  由现网 MySQL `mysqldump --no-data` 导出后，将所有 `CREATE TABLE` 改写为
  `CREATE TABLE IF NOT EXISTS`，可安全重复执行。它已包含 V2..V16 的全部结构变更。
- `migration/V*.sql` —— **增量历史脚本**，面向【已经存在的老库】，按编号顺序逐次演进。
- `apply_all.sh` —— 一键按正确顺序应用脚本（见下）。

## 如何建库 / 升级

### 场景 A：全新的空数据库（推荐给新环境）

只需执行 `schema.sql` 即可得到与现网一致的最新结构，**无需再叠加任何 V*.sql**
（结构已全部包含在 schema.sql 内）。

```bash
# 方式一：脚本（会自动建库 + 应用完整结构，一条命令搞定）
DB_NAME=ceramic DB_USER=root DB_PASSWORD=123456 ./apply_all.sh --fresh

# 方式二：手工（需先自行建库）
MYSQL_PWD=123456 mysql -uroot -e "CREATE DATABASE IF NOT EXISTS ceramic DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;"
MYSQL_PWD=123456 mysql -uroot ceramic < schema.sql
```

> 说明：`schema.sql` 只建结构、不含数据，也不建库本身。`apply_all.sh --fresh`
> 会先执行 `CREATE DATABASE IF NOT EXISTS` 再灌 schema，因此无需手工建库；
> 若走方式二直接跑 schema.sql，请先自行建库并选择该库。

### 场景 B：已存在的老库（增量升级）

按**下列精确顺序**执行 `migration/` 下的 V*.sql。所有脚本均已做幂等保护，可重复执行。

```bash
DB_NAME=ceramic DB_USER=root DB_PASSWORD=123456 ./apply_all.sh
```

精确执行顺序（**注意 V3 的两个文件**）：

1. `V2__ai_customer_service.sql`
2. `V3__audit_logs.sql`          ← 先执行 audit_logs
3. `V3_1__customization_quote.sql`  ← 再执行 customization_quote（原为重名的第二个 V3，已改名消歧义）
4. `V4__normalize_business_statuses.sql`
5. `V5__admin_stats_indexes.sql`
6. `V6__customization_flow.sql`
7. `V7__after_sales_flow.sql`
8. `V8__chat_ai_summary.sql`
9. `V9__order_shipping.sql`
10. `V10__review_reply.sql`
11. `V11__payment_gateway.sql`
12. `V12__customization_balance.sql`
13. `V13__notifications.sql`
14. `V14__shop_settings.sql`
15. `V15__product_images.sql`
16. `V16__customization_shipping_address.sql`

> 排序提示：按文件名字典序/`sort -V`，`V3_1__` 会**错误地**排在 `V3__` 之前，
> 因此不要依赖 `ls` 排序自动执行；`apply_all.sh` 里已用显式数组固定为上述正确顺序。

## 幂等性说明

- `migration/` 下的脚本统一采用 `USE ceramic;` + 基于 `INFORMATION_SCHEMA` 的
  存在性判断（`STATISTICS` 判断索引、`COLUMNS` 判断列）+ `PREPARE/EXECUTE/DEALLOCATE`
  动态语句的写法，保证可重复执行不报错。
- 建表统一使用 `CREATE TABLE IF NOT EXISTS`。

## sql-archive（危险历史脚本已隔离）

后端根目录下原先散落的一批临时脚本（`clean_users.sql`、`fix_passwords.sql`、
`reset_admin.sql`、`reset_passwords.sql`、`update_db.sql`、`update_passwords.sql`，
以及 `src/main/resources/update.sql`）已**移动**到 `platform.back/sql-archive/`，
**切勿**对真实数据库执行，详见该目录下的 README。

---

## SECURITY（安全须知，务必在真实部署前处理）

以下为当前仓库中已知的敏感默认值 / 明文回退，**上线前必须轮换并改为环境变量注入**：

- **默认账号口令**：管理员 / 顾客 / 客服的初始密码均为 `admin`（对应已提交进仓库的
  BCrypt 哈希）。上线前必须强制改密，切勿沿用committed哈希。
- **数据库口令明文回退**：`application.properties` 中开发库口令 `123456` 存在明文回退，
  本地开发可用，但生产环境必须通过环境变量注入、禁止使用该弱口令。
- **JWT 密钥明文回退**：JWT secret 同样在 `application.properties` 中有明文回退值，
  一旦泄露即可伪造任意用户令牌，上线前必须替换为强随机密钥并由环境变量注入。

> 本次改造未修改任何 `*.properties` 文件（本地开发回退需保持可用），此处仅作风险提示。

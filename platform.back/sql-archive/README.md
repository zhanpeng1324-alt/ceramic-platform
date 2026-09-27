# sql-archive —— 危险历史脚本归档（请勿执行）

本目录收纳了后端历史遗留的**临时/一次性 SQL 脚本**。它们原先散落在
`platform.back/` 根目录及 `src/main/resources/` 下，容易被误执行，现统一移动到此隔离。

> ⚠️ **严禁**对任何真实（生产或含有效数据的）数据库执行本目录中的脚本。
> 它们仅作历史留存参考。正式建库/升级请使用上级目录
> `src/main/resources/db/` 下的 `schema.sql` 与 `migration/V*.sql`（见其 README.md）。

## 为什么危险

### 1. 破坏性的用户删除
- `clean_users.sql`：对 `id IN (2,3,6,7)` 的用户执行**级联硬删除**，
  牵连 orders、order_items、reviews、custom_orders、chat_* 等十余张表，数据不可恢复。

### 2. 口令哈希彼此矛盾（跨文件不一致）
不同脚本把用户密码重置成**三种互相冲突的 BCrypt 哈希**，先后执行会互相覆盖，
导致“到底哪个才是当前密码”完全不可预测：
- `$2a$10$N9qo8uLOickgx2ZMRZoMye.IjzqAKL9xL5jvMFVdNJHvGCgTq/VEq`
  —— 见 `fix_passwords.sql`、`reset_passwords.sql`、`clean_users.sql`、`update.sql`、`update_db.sql`
- `$2a$10$KFkJG2U5kSIWATlA11GEOuADY2gqTn3MMzvKWSCu64tc1Voy4PTya`
  —— 见 `reset_admin.sql`（仅重置 admin）
- `$2a$10$PJXnI7GlYFu5NWkZaUHFiuKN0d7D3U4N1cNsPGJkJw.hfedH.s.Pq`
  —— 见 `update_passwords.sql`

（提醒：这些明文口令普遍就是弱口令 `admin`，属于已知安全隐患，切勿带入生产。）

### 3. `update.sql` 会写入与规范相悖的小写状态
- `update.sql` 执行 `UPDATE orders SET status = 'paid' WHERE status = 'PENDING_PAY'`，
  写入**小写 `paid`**，这与 `migration/V4__normalize_business_statuses.sql` 确立的
  大写业务状态规范直接冲突，会把已归一化的数据重新污染成小写。
- 该文件此前位于 `src/main/resources/update.sql`。已确认它是**惰性文件**：
  仓库中除 `application.properties` 里的 `spring.sql.init.mode=never`（该配置本就禁止
  Spring 启动时自动执行 SQL）外，没有任何代码引用它，因此移动它不影响应用运行。

## 文件清单

| 文件 | 来源 | 主要危害 |
|------|------|----------|
| `clean_users.sql` | 根目录 | 级联硬删除用户及其全部关联数据 |
| `fix_passwords.sql` | 根目录 | 批量重置口令（哈希版本 N9qo…） |
| `reset_admin.sql` | 根目录 | 重置 admin 口令（哈希版本 KFkJ…，与其他文件不一致） |
| `reset_passwords.sql` | 根目录 | 批量重置口令（哈希版本 N9qo…） |
| `update_passwords.sql` | 根目录 | 批量重置口令（哈希版本 PJXn…，与其他文件不一致） |
| `update_db.sql` | 根目录 | 临时补列 + 插入 service 用户 |
| `update.sql` | `src/main/resources/` | 写入小写 `paid` 状态，违反 V4 状态规范 |

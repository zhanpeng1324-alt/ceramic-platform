-- V9: 订单发货物流字段（单商家自营）
-- 新增可空字段：快递公司、快递单号、发货时间。纯增量，不影响现有数据。
-- 采用信息schema判断，脚本可重复执行。
USE ceramic;

SET @has_company = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='ceramic' AND TABLE_NAME='orders' AND COLUMN_NAME='shipping_company');
SET @sql = IF(@has_company=0,
    'ALTER TABLE orders ADD COLUMN shipping_company VARCHAR(50) NULL AFTER pay_time',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_no = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='ceramic' AND TABLE_NAME='orders' AND COLUMN_NAME='tracking_no');
SET @sql = IF(@has_no=0,
    'ALTER TABLE orders ADD COLUMN tracking_no VARCHAR(64) NULL AFTER shipping_company',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_time = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='ceramic' AND TABLE_NAME='orders' AND COLUMN_NAME='ship_time');
SET @sql = IF(@has_time=0,
    'ALTER TABLE orders ADD COLUMN ship_time DATETIME NULL AFTER tracking_no',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

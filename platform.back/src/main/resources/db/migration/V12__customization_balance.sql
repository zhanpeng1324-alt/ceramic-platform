-- 定制尾款支付：给 custom_orders 增加尾款相关列（幂等：先查 INFORMATION_SCHEMA 再 ALTER）
-- final_amount = quoted_price - deposit_amount，在管理员报价时一并算出并写入。
USE ceramic;

SET @has_final_amount = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='ceramic' AND TABLE_NAME='custom_orders' AND COLUMN_NAME='final_amount');
SET @sql = IF(@has_final_amount=0,
    'ALTER TABLE custom_orders ADD COLUMN final_amount DECIMAL(10,2) NULL COMMENT ''尾款金额'' AFTER deposit_amount',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_final_paid = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='ceramic' AND TABLE_NAME='custom_orders' AND COLUMN_NAME='final_paid');
SET @sql = IF(@has_final_paid=0,
    'ALTER TABLE custom_orders ADD COLUMN final_paid TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''尾款是否已支付'' AFTER deposit_paid',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_final_pay_time = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='ceramic' AND TABLE_NAME='custom_orders' AND COLUMN_NAME='final_pay_time');
SET @sql = IF(@has_final_pay_time=0,
    'ALTER TABLE custom_orders ADD COLUMN final_pay_time DATETIME NULL COMMENT ''尾款支付时间''',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

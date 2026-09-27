-- V3_1: custom_orders 增加报价与进度说明字段
-- 幂等执行：通过 INFORMATION_SCHEMA.COLUMNS 判断列是否已存在，避免重复添加报错。
-- 在 ceramic 库中执行一次。
USE ceramic;

-- quoted_price：管理员报价
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'custom_orders' AND column_name = 'quoted_price');
SET @sql := IF(@exist = 0, 'ALTER TABLE custom_orders ADD COLUMN quoted_price DECIMAL(10,2) NULL COMMENT ''管理员报价''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- timeline_note：定制进度说明
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'custom_orders' AND column_name = 'timeline_note');
SET @sql := IF(@exist = 0, 'ALTER TABLE custom_orders ADD COLUMN timeline_note VARCHAR(500) NULL COMMENT ''定制进度说明''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

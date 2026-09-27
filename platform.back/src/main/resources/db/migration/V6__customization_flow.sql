-- V6: 陶瓷定制业务闭环改造
-- 1. custom_orders 增加预计完成日期字段
-- 2. customization_progress 表补全（含操作人、状态变更记录）
-- 3. 历史状态归一化：QUOTE_CONFIRMED -> CONFIRMED
-- 在 ceramic 库中执行一次，幂等。

-- ========== custom_orders 增加预计完成日期 ==========
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'custom_orders' AND column_name = 'expected_complete_date');
SET @sql := IF(@exist = 0, 'ALTER TABLE custom_orders ADD COLUMN expected_complete_date DATE NULL COMMENT ''预计完成日期''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 防御性补齐 custom_orders 既往字段（部分环境可能缺失）
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'custom_orders' AND column_name = 'quoted_price');
SET @sql := IF(@exist = 0, 'ALTER TABLE custom_orders ADD COLUMN quoted_price DECIMAL(10,2) NULL COMMENT ''管理员报价''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'custom_orders' AND column_name = 'deposit_amount');
SET @sql := IF(@exist = 0, 'ALTER TABLE custom_orders ADD COLUMN deposit_amount DECIMAL(10,2) NULL COMMENT ''定金金额''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'custom_orders' AND column_name = 'deposit_paid');
SET @sql := IF(@exist = 0, 'ALTER TABLE custom_orders ADD COLUMN deposit_paid TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''定金是否已付''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'custom_orders' AND column_name = 'timeline_note');
SET @sql := IF(@exist = 0, 'ALTER TABLE custom_orders ADD COLUMN timeline_note VARCHAR(500) NULL COMMENT ''制作进度说明''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'custom_orders' AND column_name = 'finished_product_url');
SET @sql := IF(@exist = 0, 'ALTER TABLE custom_orders ADD COLUMN finished_product_url VARCHAR(500) NULL COMMENT ''成品图URL''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== customization_progress 表 ==========
-- 若表不存在则按完整结构创建；若已存在则补齐新字段。
CREATE TABLE IF NOT EXISTS customization_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customization_id BIGINT NOT NULL COMMENT '定制单ID',
    stage VARCHAR(64) NOT NULL COMMENT '阶段/状态名称',
    description VARCHAR(1000) NULL COMMENT '说明',
    image_url VARCHAR(500) NULL COMMENT '阶段图片',
    operator_id BIGINT NULL COMMENT '操作人ID',
    operator_role VARCHAR(32) NULL COMMENT '操作人角色',
    from_status VARCHAR(32) NULL COMMENT '变更前状态',
    to_status VARCHAR(32) NULL COMMENT '变更后状态',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_progress_custom (customization_id),
    INDEX idx_progress_created (created_at)
);

-- 补齐操作人与状态变更字段（已存在则跳过）
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'customization_progress' AND column_name = 'operator_id');
SET @sql := IF(@exist = 0, 'ALTER TABLE customization_progress ADD COLUMN operator_id BIGINT NULL COMMENT ''操作人ID''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'customization_progress' AND column_name = 'operator_role');
SET @sql := IF(@exist = 0, 'ALTER TABLE customization_progress ADD COLUMN operator_role VARCHAR(32) NULL COMMENT ''操作人角色''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'customization_progress' AND column_name = 'from_status');
SET @sql := IF(@exist = 0, 'ALTER TABLE customization_progress ADD COLUMN from_status VARCHAR(32) NULL COMMENT ''变更前状态''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'customization_progress' AND column_name = 'to_status');
SET @sql := IF(@exist = 0, 'ALTER TABLE customization_progress ADD COLUMN to_status VARCHAR(32) NULL COMMENT ''变更后状态''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== 历史状态归一化 ==========
-- 统一定制单状态：PENDING、QUOTED、CONFIRMED、IN_PROGRESS、QUALITY_CHECK、COMPLETED、CANCELLED、REJECTED
UPDATE custom_orders SET status = 'CONFIRMED' WHERE status = 'QUOTE_CONFIRMED';
UPDATE custom_orders SET status = UPPER(status) WHERE status IN ('pending','approved','rejected','in_progress','completed','cancelled');
UPDATE custom_orders SET status = 'QUOTED' WHERE status = 'APPROVED';
UPDATE custom_orders SET status = 'REJECTED' WHERE status = 'REJECTED';
UPDATE custom_orders SET status = 'CANCELLED' WHERE status = 'CANCELLED';

-- customization_progress 历史状态同样归一化
UPDATE customization_progress SET from_status = 'CONFIRMED' WHERE from_status = 'QUOTE_CONFIRMED';
UPDATE customization_progress SET to_status = 'CONFIRMED' WHERE to_status = 'QUOTE_CONFIRMED';

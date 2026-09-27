-- V7: 售后服务模块完善
-- 1. return_requests 补全凭证图片、退货地址字段
-- 2. 新增 return_request_logs 表记录每次状态变更
-- 3. 历史售后状态归一化为大写
-- 在 ceramic 库中执行一次，幂等。

-- ========== return_requests 补全字段 ==========
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'return_requests' AND column_name = 'evidence_images');
SET @sql := IF(@exist = 0, 'ALTER TABLE return_requests ADD COLUMN evidence_images TEXT NULL COMMENT ''凭证图片URL(JSON数组)''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'return_requests' AND column_name = 'return_address');
SET @sql := IF(@exist = 0, 'ALTER TABLE return_requests ADD COLUMN return_address VARCHAR(500) NULL COMMENT ''退货地址''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ========== return_request_logs 日志表 ==========
CREATE TABLE IF NOT EXISTS return_request_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    return_request_id BIGINT NOT NULL COMMENT '售后单ID',
    from_status VARCHAR(32) NULL COMMENT '变更前状态',
    to_status VARCHAR(32) NOT NULL COMMENT '变更后状态',
    operator_id BIGINT NULL COMMENT '操作人ID',
    operator_role VARCHAR(32) NULL COMMENT '操作人角色',
    note VARCHAR(1000) NULL COMMENT '操作说明',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rrl_request (return_request_id),
    INDEX idx_rrl_created (created_at)
);

-- ========== 历史状态归一化 ==========
-- 统一售后状态：PENDING、APPROVED、REJECTED、RETURNING、RECEIVED、REFUNDING、REFUNDED、CLOSED
UPDATE return_requests SET status = UPPER(status) WHERE status IN ('pending','approved','rejected','returning','received','refunding','refunded','closed');
UPDATE return_requests SET status = 'REFUNDED' WHERE status = 'COMPLETED';
UPDATE return_requests SET status = 'RECEIVED' WHERE status = 'SHIPPED_BACK';

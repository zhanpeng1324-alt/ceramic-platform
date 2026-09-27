-- V10: 评价商家回复字段（单商家自营）
-- 新增可空字段：商家回复内容、回复时间。纯增量，不影响现有数据。
-- 采用信息schema判断，脚本可重复执行。
USE ceramic;

SET @has_reply = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='ceramic' AND TABLE_NAME='reviews' AND COLUMN_NAME='reply');
SET @sql = IF(@has_reply=0,
    'ALTER TABLE reviews ADD COLUMN reply TEXT NULL AFTER status',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @has_reply_time = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='ceramic' AND TABLE_NAME='reviews' AND COLUMN_NAME='reply_time');
SET @sql = IF(@has_reply_time=0,
    'ALTER TABLE reviews ADD COLUMN reply_time DATETIME NULL AFTER reply',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

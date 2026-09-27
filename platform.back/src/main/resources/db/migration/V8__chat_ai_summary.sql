-- V8: AI 转人工会话摘要字段
-- 幂等执行：通过 INFORMATION_SCHEMA.COLUMNS 判断列是否已存在，避免重复添加报错。
-- 在 ceramic 库中执行一次。
USE ceramic;

-- ai_summary：AI 转人工时的对话摘要
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_schema = 'ceramic' AND table_name = 'chat_conversations' AND column_name = 'ai_summary');
SET @sql := IF(@exist = 0, 'ALTER TABLE chat_conversations ADD COLUMN ai_summary TEXT NULL COMMENT ''AI 转人工时的对话摘要''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

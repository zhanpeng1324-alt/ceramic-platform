-- V2: AI 客服会话历史查询索引
-- 这些索引让聊天历史查询保持高效。
-- 幂等执行：MySQL 的 CREATE INDEX 不支持 IF NOT EXISTS，改用 INFORMATION_SCHEMA.STATISTICS
-- 判断索引是否已存在，避免重复创建报错。在 ceramic 库中执行一次。
USE ceramic;

-- chat_messages (conversation_id, timestamp)：按会话拉取聊天记录
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema = 'ceramic' AND table_name = 'chat_messages' AND index_name = 'idx_chat_message_conversation_time');
SET @sql := IF(@exist = 0, 'CREATE INDEX idx_chat_message_conversation_time ON chat_messages (conversation_id, timestamp)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- chat_conversations (status, updated_at)：按状态与更新时间列出会话
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema = 'ceramic' AND table_name = 'chat_conversations' AND index_name = 'idx_chat_conversation_status_updated');
SET @sql := IF(@exist = 0, 'CREATE INDEX idx_chat_conversation_status_updated ON chat_conversations (status, updated_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

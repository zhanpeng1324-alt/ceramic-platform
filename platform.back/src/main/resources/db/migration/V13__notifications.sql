-- 站内消息通知中心
-- 订单/定制/售后状态推进后向用户投递一条通知，用户可在通知中心查看并标记已读。
-- type: ORDER / CUSTOMIZATION / RETURN / SYSTEM
USE ceramic;

CREATE TABLE IF NOT EXISTS notifications (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '接收用户',
    type        VARCHAR(20)  NOT NULL COMMENT 'ORDER/CUSTOMIZATION/RETURN/SYSTEM',
    title       VARCHAR(100) NOT NULL COMMENT '通知标题',
    content     VARCHAR(500) NULL COMMENT '通知正文',
    biz_type    VARCHAR(20)  NULL COMMENT '关联业务类型',
    biz_id      BIGINT       NULL COMMENT '关联业务单ID',
    is_read     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at     DATETIME     NULL,
    INDEX idx_user_unread (user_id, is_read),
    INDEX idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知';

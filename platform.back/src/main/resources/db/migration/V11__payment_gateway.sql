-- 模拟支付网关：支付流水表
-- 承载「发起支付 → 网关回调 → 业务状态推进」链路的流水记录，替换原本「直接翻状态」的演示支付。
-- biz_type: ORDER(订单) / CUSTOM_DEPOSIT(定制定金) / CUSTOM_BALANCE(定制尾款)
-- status:   PENDING(待支付) / SUCCESS(已支付) / FAILED(支付失败) / REFUNDED(已退款)
USE ceramic;

CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_no      VARCHAR(40)  NOT NULL COMMENT '支付流水号(业务唯一)',
    biz_type        VARCHAR(20)  NOT NULL COMMENT 'ORDER/CUSTOM_DEPOSIT/CUSTOM_BALANCE',
    biz_id          BIGINT       NOT NULL COMMENT '关联业务单ID',
    user_id         BIGINT       NOT NULL COMMENT '付款用户',
    amount          DECIMAL(10,2) NOT NULL COMMENT '应付金额',
    channel         VARCHAR(20)  NULL COMMENT '模拟支付渠道 alipay/wechat/bank',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED/REFUNDED',
    transaction_id  VARCHAR(64)  NULL COMMENT '模拟第三方交易号',
    refund_no       VARCHAR(40)  NULL COMMENT '退款流水号',
    refund_amount   DECIMAL(10,2) NULL COMMENT '退款金额',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at         DATETIME     NULL,
    refunded_at     DATETIME     NULL,
    UNIQUE KEY uk_payment_no (payment_no),
    INDEX idx_user (user_id),
    INDEX idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模拟支付流水';

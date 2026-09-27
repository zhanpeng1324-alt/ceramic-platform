-- 店铺设置（单商户，全表仅一行）：发货寄件方信息，兼作默认退货地址
USE ceramic;

CREATE TABLE IF NOT EXISTS shop_settings (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    shop_name     VARCHAR(100) NULL COMMENT '店铺名称',
    contact_name  VARCHAR(50)  NULL COMMENT '联系人/寄件人',
    contact_phone VARCHAR(30)  NULL COMMENT '联系电话',
    address       VARCHAR(255) NULL COMMENT '店铺地址(发货地/默认退货地址)',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺设置(单商户单行)';

-- 初始化单行（幂等：仅当空表时插入一行占位，供管理员后续编辑）
INSERT INTO shop_settings (shop_name, contact_name, contact_phone, address)
SELECT '青瓷坊', '', '', ''
WHERE NOT EXISTS (SELECT 1 FROM shop_settings);

-- 定制收货地址：给 custom_orders 增加 shipping_address 列，
-- 定制成品完成后据此发货（联系人/电话已有 contact_name/contact_phone）。
-- 幂等：先查 INFORMATION_SCHEMA 再 ALTER。
USE ceramic;

SET @has_ship = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='ceramic' AND TABLE_NAME='custom_orders' AND COLUMN_NAME='shipping_address');
SET @sql = IF(@has_ship=0,
    'ALTER TABLE custom_orders ADD COLUMN shipping_address VARCHAR(500) NULL COMMENT ''收货地址'' AFTER contact_phone',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

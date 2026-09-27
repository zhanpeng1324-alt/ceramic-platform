-- V5: 管理员数据看板统计查询性能优化索引
-- 幂等执行：通过 INFORMATION_SCHEMA 判断索引是否已存在，避免重复创建报错。
-- 在 ceramic 库中执行一次。

-- orders.created_at：近 7 天订单量/销售额按日期聚合
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema = 'ceramic' AND table_name = 'orders' AND index_name = 'idx_orders_created_at');
SET @sql := IF(@exist = 0, 'ALTER TABLE orders ADD INDEX idx_orders_created_at (created_at)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- orders.status：订单状态分布、待支付订单计数
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema = 'ceramic' AND table_name = 'orders' AND index_name = 'idx_orders_status');
SET @sql := IF(@exist = 0, 'ALTER TABLE orders ADD INDEX idx_orders_status (status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- products.stock,status：低库存商品查询
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema = 'ceramic' AND table_name = 'products' AND index_name = 'idx_products_stock_status');
SET @sql := IF(@exist = 0, 'ALTER TABLE products ADD INDEX idx_products_stock_status (stock, status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- custom_orders.status：待审核定制计数
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema = 'ceramic' AND table_name = 'custom_orders' AND index_name = 'idx_custom_orders_status');
SET @sql := IF(@exist = 0, 'ALTER TABLE custom_orders ADD INDEX idx_custom_orders_status (status)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- order_items.product_id：热销商品 Top5 聚合
SET @exist := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE table_schema = 'ceramic' AND table_name = 'order_items' AND index_name = 'idx_order_items_product');
SET @sql := IF(@exist = 0, 'ALTER TABLE order_items ADD INDEX idx_order_items_product (product_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

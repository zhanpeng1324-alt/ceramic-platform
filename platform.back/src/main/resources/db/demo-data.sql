-- =====================================================================
-- demo-data.sql —— 演示种子数据（可安全公开，供克隆者开箱体验）
-- ---------------------------------------------------------------------
-- 用途：为一个已建好结构的空库灌入最小演示数据，让克隆者能立即
--       登录后台、浏览商品、下单与秒杀，无需手工录数据。
--
-- 幂等：全部使用固定 id + INSERT IGNORE，可重复执行不会重复插入。
--
-- ⚠️ 安全须知：本文件仅用于演示 / 本地开发。三个演示账号初始密码均为
--    `admin`（对应下方 BCrypt 哈希）。生产环境必须删除演示账号或强制改密，
--    切勿沿用此处口令。
-- =====================================================================

-- 演示账号（密码均为 admin）：管理员 / 客服 / 顾客三种角色
-- 手机号验证码登录时（演示短信，验证码回显在前端），会按手机号匹配到这些账号，
-- 从而以对应角色登录；例如 13800000000 即可进入管理员后台。
INSERT IGNORE INTO `users`
  (`id`, `username`, `nickname`, `email`, `phone`, `password`, `address`, `role`) VALUES
  (1, 'admin',    '平台管理员', 'admin@example.com',    '13800000000', '$2a$10$biRfWyXUaUezAihtBx2Q2.8ETmf/bwYFt8.07VVyj/ub2C09ddvgq', '景德镇陶瓷大道 88 号', 'admin'),
  (2, 'service',  '客服专员',   'service@example.com',  '13800000003', '$2a$10$biRfWyXUaUezAihtBx2Q2.8ETmf/bwYFt8.07VVyj/ub2C09ddvgq', NULL,                  'service'),
  (3, 'customer', '演示顾客',   'customer@example.com', '13800000001', '$2a$10$biRfWyXUaUezAihtBx2Q2.8ETmf/bwYFt8.07VVyj/ub2C09ddvgq', NULL,                  'customer');

-- 演示商品分类
INSERT IGNORE INTO `categories` (`id`, `name`, `description`, `sort_order`) VALUES
  (1, '茶具', '茶壶、茶杯、公道杯等陶瓷茶具', 1),
  (2, '餐具', '碗、盘、碟等日用陶瓷餐具',   2),
  (3, '花器', '花瓶、花插等陶瓷摆件',       3);

-- 演示商品（含可定制项与足量库存，便于体验下单 / 秒杀）
INSERT IGNORE INTO `products`
  (`id`, `category_id`, `name`, `subtitle`, `description`, `price`, `stock`, `image_url`, `glaze_color`, `material`, `size`, `customizable`, `status`) VALUES
  (1, 1, '青瓷莲纹盖碗',   '手工拉坯 · 龙泉青瓷', '龙泉青瓷手作盖碗，釉色温润，适合功夫茶冲泡。',       298.00, 200, '/uploads/demo-gaiwan.jpg',  '梅子青', '瓷', '150ml',       0, 'active'),
  (2, 1, '汝窑天青茶杯',   '开片天青 · 主人杯',   '仿汝窑天青釉主人杯，静置可见细密开片。',             128.00, 300, '/uploads/demo-cup.jpg',     '天青',   '瓷', '80ml',        1, 'active'),
  (3, 2, '白瓷描金饭碗',   '骨瓷 · 描金边',       '骨瓷饭碗，碗口描金，日常餐用亦可送礼。',             88.00,  500, '/uploads/demo-bowl.jpg',    '象牙白', '骨瓷', '4.5 寸',    1, 'active'),
  (4, 3, '钧窑窑变花瓶',   '窑变釉 · 客厅摆件',   '钧窑窑变釉花瓶，每件釉色独一无二，适合客厅陈设。',   458.00, 120, '/uploads/demo-vase.jpg',    '玫瑰紫', '瓷', '高 28cm',     0, 'active');

-- 店铺设置（若 shop_settings 尚无行则补一行，与 V14 一致）
INSERT INTO `shop_settings` (`shop_name`, `contact_name`, `contact_phone`, `address`)
SELECT '青瓷坊', '店主', '13800000000', '景德镇陶瓷大道 88 号'
WHERE NOT EXISTS (SELECT 1 FROM `shop_settings`);

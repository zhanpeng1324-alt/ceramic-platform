-- 商品多图：给 products 增加 images 列（JSON 数组字符串，存多张图片 URL）。
-- 幂等：先查 INFORMATION_SCHEMA 再 ALTER。首图仍复用原 image_url 作为封面，
-- images 为空时前端回退到 image_url，保证老数据不受影响。
USE ceramic;

SET @has_images = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA='ceramic' AND TABLE_NAME='products' AND COLUMN_NAME='images');
SET @sql = IF(@has_images=0,
    'ALTER TABLE products ADD COLUMN images TEXT NULL COMMENT ''商品图册(JSON数组，首图为封面)'' AFTER image_url',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

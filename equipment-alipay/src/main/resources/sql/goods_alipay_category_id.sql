SET @column_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'goods'
    AND COLUMN_NAME = 'alipay_category_id'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE goods ADD COLUMN alipay_category_id varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT ''支付宝普通商品类目ID'' AFTER alipay_goods_id',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'goods'
    AND COLUMN_NAME = 'alipay_rent_category_id'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE goods ADD COLUMN alipay_rent_category_id varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT ''支付宝租赁信用类目ID'' AFTER alipay_category_id',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

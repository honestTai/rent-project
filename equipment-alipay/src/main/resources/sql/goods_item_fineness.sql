SET @column_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'goods'
    AND COLUMN_NAME = 'item_fineness'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE goods ADD COLUMN item_fineness varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT ''secondHand'' COMMENT ''支付宝商品成色：wholeNew全新 secondHand二手'' AFTER alipay_goods_id',
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
    AND COLUMN_NAME = 'item_fineness_grade'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE goods ADD COLUMN item_fineness_grade varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT ''95new'' COMMENT ''支付宝商品成色等级'' AFTER item_fineness',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE goods
SET item_fineness = 'secondHand'
WHERE item_fineness IS NULL OR item_fineness = '' OR item_fineness NOT IN ('wholeNew', 'secondHand');

UPDATE goods
SET item_fineness_grade = '95new'
WHERE item_fineness_grade IS NULL
   OR item_fineness_grade = ''
   OR item_fineness_grade NOT IN ('99new', '95new', '90new', '80new', '70new');

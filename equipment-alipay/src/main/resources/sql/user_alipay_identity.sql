-- Store both Alipay user_id (2088...) and open_id for miniapp users.
SET @column_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user'
    AND COLUMN_NAME = 'alipay_user_id'
);

SET @ddl := IF(
  @column_exists = 0,
  'ALTER TABLE `user` ADD COLUMN `alipay_user_id` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT ''支付宝用户ID（2088开头）'' AFTER `uuid`',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user'
    AND INDEX_NAME = 'idx_user_alipay_user_id'
);

SET @ddl := IF(
  @index_exists = 0,
  'ALTER TABLE `user` ADD KEY `idx_user_alipay_user_id` (`alipay_user_id`)',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

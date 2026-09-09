SET @schema_name = DATABASE();

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `rent_order` ADD COLUMN `contract_alipay_file_id` varchar(128) DEFAULT NULL COMMENT ''支付宝合同文件file_id'' AFTER `contract_snapshot_json`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'rent_order' AND COLUMN_NAME = 'contract_alipay_file_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `rent_order` ADD COLUMN `contract_alipay_sync_status` varchar(32) DEFAULT NULL COMMENT ''合同回传支付宝状态：SUCCESS/FAILED'' AFTER `contract_alipay_file_id`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'rent_order' AND COLUMN_NAME = 'contract_alipay_sync_status'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `rent_order` ADD COLUMN `contract_alipay_synced_at` bigint DEFAULT NULL COMMENT ''合同最近回传支付宝时间戳'' AFTER `contract_alipay_sync_status`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'rent_order' AND COLUMN_NAME = 'contract_alipay_synced_at'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `rent_order` ADD COLUMN `contract_alipay_sync_error` varchar(1024) DEFAULT NULL COMMENT ''合同回传支付宝失败原因'' AFTER `contract_alipay_synced_at`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'rent_order' AND COLUMN_NAME = 'contract_alipay_sync_error'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

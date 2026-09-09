-- 正式分类目录结构迁移（MySQL 8，幂等）。
-- 执行顺序：本文件 -> 人工填充 rent_catalog_goods_category_mapping -> rent_catalog_goods_mapping_apply.sql。
SET @schema_name = DATABASE();

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `category` ADD COLUMN `code` varchar(64) DEFAULT NULL COMMENT ''稳定分类编码'' AFTER `category_id`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND COLUMN_NAME='code');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `category` ADD COLUMN `parent_code` varchar(64) DEFAULT NULL COMMENT ''父分类编码'' AFTER `code`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND COLUMN_NAME='parent_code');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `category` ADD COLUMN `short_name` varchar(32) DEFAULT NULL COMMENT ''小程序简称'' AFTER `name`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND COLUMN_NAME='short_name');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `category` ADD COLUMN `description` varchar(512) DEFAULT NULL COMMENT ''分类说明'' AFTER `short_name`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND COLUMN_NAME='description');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `category` ADD COLUMN `cover_image` varchar(1024) DEFAULT NULL COMMENT ''完整 HTTPS 分类封面'' AFTER `description`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND COLUMN_NAME='cover_image');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `category` ADD COLUMN `icon_name` varchar(64) DEFAULT NULL COMMENT ''antd-mini 分类图标名称'' AFTER `cover_image`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND COLUMN_NAME='icon_name');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `category` ADD COLUMN `sort_order` int NOT NULL DEFAULT 100 COMMENT ''稳定排序'' AFTER `cover_image`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND COLUMN_NAME='sort_order');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `category` ADD COLUMN `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER `status`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND COLUMN_NAME='created_at');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `category` ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND COLUMN_NAME='updated_at');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `goods` ADD COLUMN `category_code` varchar(64) DEFAULT NULL COMMENT ''叶子分类编码'' AFTER `sort_order`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='goods' AND COLUMN_NAME='category_code');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `goods` ADD COLUMN `brand` varchar(128) DEFAULT NULL AFTER `category_code`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='goods' AND COLUMN_NAME='brand');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `goods` ADD COLUMN `model_name` varchar(128) DEFAULT NULL AFTER `brand`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='goods' AND COLUMN_NAME='model_name');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `goods` ADD COLUMN `device_type` varchar(64) DEFAULT NULL AFTER `model_name`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='goods' AND COLUMN_NAME='device_type');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `goods` ADD COLUMN `default_rent_unit` varchar(16) DEFAULT NULL COMMENT ''显式默认单位；为空时按主 SKU 推导'' AFTER `device_type`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='goods' AND COLUMN_NAME='default_rent_unit');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `goods` ADD COLUMN `featured` tinyint NOT NULL DEFAULT 0 AFTER `default_rent_unit`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='goods' AND COLUMN_NAME='featured');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `goods` ADD COLUMN `featured_sort` bigint NOT NULL DEFAULT 0 AFTER `featured`',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='goods' AND COLUMN_NAME='featured_sort');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 先复用完全同名的旧分类行，避免重复展示分类；不会根据商品标题做任何映射。
UPDATE category SET code='drone' WHERE code IS NULL AND name='无人机'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='drone')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='drone-portable' WHERE code IS NULL AND name='轻量航拍'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='drone-portable')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='drone-pro' WHERE code IS NULL AND name='专业航拍'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='drone-pro')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='drone-fpv' WHERE code IS NULL AND name='FPV 穿越机'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='drone-fpv')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='drone-industry' WHERE code IS NULL AND name='行业作业'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='drone-industry')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='handheld' WHERE code IS NULL AND name='手持影像'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='handheld')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='camera-action' WHERE code IS NULL AND name='运动相机'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='camera-action')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='camera-pocket' WHERE code IS NULL AND name='口袋相机'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='camera-pocket')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='camera-stabilizer' WHERE code IS NULL AND name='相机稳定器'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='camera-stabilizer')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='accessory' WHERE code IS NULL AND name='配件服务'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='accessory')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='accessory-power' WHERE code IS NULL AND name='电源与续航'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='accessory-power')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='accessory-shoot' WHERE code IS NULL AND name='拍摄配件'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='accessory-shoot')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='accessory-audio' WHERE code IS NULL AND name='音频配件'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='accessory-audio')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='accessory-service' WHERE code IS NULL AND name='保障服务'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='accessory-service')=0 ORDER BY category_id LIMIT 1;
UPDATE category SET code='other' WHERE code IS NULL AND name='其他'
  AND (SELECT COUNT(*) FROM (SELECT code FROM category) existing WHERE code='other')=0 ORDER BY category_id LIMIT 1;

INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'drone',NULL,'无人机','无人机','航拍、旅拍与专业作业设备','CompassOutline',10,1
WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='drone');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'drone-portable','drone','轻量航拍','轻量航拍','便携航拍机型','TravelOutline',10,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='drone-portable');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'drone-pro','drone','专业航拍','专业航拍','专业影像航拍机型','CameraOutline',20,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='drone-pro');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'drone-fpv','drone','FPV 穿越机','FPV','沉浸式航拍设备','PlayOutline',30,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='drone-fpv');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'drone-industry','drone','行业作业','行业作业','行业级专业作业设备','CompassOutline',40,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='drone-industry');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'handheld',NULL,'手持影像','手持影像','手持拍摄与稳定设备','VideoOutline',20,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='handheld');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'camera-action','handheld','运动相机','运动相机','运动与户外拍摄设备','VideoOutline',10,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='camera-action');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'camera-pocket','handheld','口袋相机','口袋相机','便携口袋影像设备','CameraOutline',20,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='camera-pocket');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'camera-stabilizer','handheld','相机稳定器','稳定器','相机与手机稳定设备','LoopOutline',30,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='camera-stabilizer');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'accessory',NULL,'配件服务','配件服务','电源、拍摄、音频与保障服务','SetOutline',30,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='accessory');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'accessory-power','accessory','电源与续航','电源续航','电池、充电与续航配件','SetOutline',10,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='accessory-power');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'accessory-shoot','accessory','拍摄配件','拍摄配件','拍摄辅助配件','CameraOutline',20,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='accessory-shoot');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'accessory-audio','accessory','音频配件','音频配件','录音与收音设备','AudioOutline',30,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='accessory-audio');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'accessory-service','accessory','保障服务','保障服务','设备保障与配套服务','CheckShieldOutline',40,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='accessory-service');
INSERT INTO category (code,parent_code,name,short_name,description,icon_name,sort_order,status)
SELECT 'other',NULL,'其他','其他','待确认或未归入上述目录的设备','AppOutline',90,1 WHERE NOT EXISTS (SELECT 1 FROM category WHERE code='other');

-- 对复用的旧分类行补齐稳定层级与简称；不覆盖运营已维护的名称、说明和启停状态。
UPDATE category SET
  parent_code=CASE
    WHEN code IN ('drone-portable','drone-pro','drone-fpv','drone-industry') THEN 'drone'
    WHEN code IN ('camera-action','camera-pocket','camera-stabilizer') THEN 'handheld'
    WHEN code IN ('accessory-power','accessory-shoot','accessory-audio','accessory-service') THEN 'accessory'
    ELSE NULL END,
  short_name=COALESCE(NULLIF(short_name,''), name),
  sort_order=CASE code
    WHEN 'drone' THEN 10 WHEN 'handheld' THEN 20 WHEN 'accessory' THEN 30 WHEN 'other' THEN 90
    WHEN 'drone-portable' THEN 10 WHEN 'drone-pro' THEN 20 WHEN 'drone-fpv' THEN 30 WHEN 'drone-industry' THEN 40
    WHEN 'camera-action' THEN 10 WHEN 'camera-pocket' THEN 20 WHEN 'camera-stabilizer' THEN 30
    WHEN 'accessory-power' THEN 10 WHEN 'accessory-shoot' THEN 20 WHEN 'accessory-audio' THEN 30 WHEN 'accessory-service' THEN 40
    ELSE sort_order END
WHERE code IN ('drone','drone-portable','drone-pro','drone-fpv','drone-industry',
  'handheld','camera-action','camera-pocket','camera-stabilizer',
  'accessory','accessory-power','accessory-shoot','accessory-audio','accessory-service','other');

-- 仅为空的正式目录补齐 antd-mini Icon；重复执行不会覆盖后台后续选择的合法图标。
UPDATE category SET icon_name=CASE code
  WHEN 'drone' THEN 'CompassOutline'
  WHEN 'handheld' THEN 'VideoOutline'
  WHEN 'accessory' THEN 'SetOutline'
  WHEN 'other' THEN 'AppOutline'
  WHEN 'drone-portable' THEN 'TravelOutline'
  WHEN 'drone-pro' THEN 'CameraOutline'
  WHEN 'drone-fpv' THEN 'PlayOutline'
  WHEN 'drone-industry' THEN 'CompassOutline'
  WHEN 'camera-action' THEN 'VideoOutline'
  WHEN 'camera-pocket' THEN 'CameraOutline'
  WHEN 'camera-stabilizer' THEN 'LoopOutline'
  WHEN 'accessory-power' THEN 'SetOutline'
  WHEN 'accessory-shoot' THEN 'CameraOutline'
  WHEN 'accessory-audio' THEN 'AudioOutline'
  WHEN 'accessory-service' THEN 'CheckShieldOutline'
  ELSE icon_name END
WHERE code IN ('drone','handheld','accessory','other','drone-portable','drone-pro','drone-fpv','drone-industry',
  'camera-action','camera-pocket','camera-stabilizer','accessory-power','accessory-shoot','accessory-audio','accessory-service')
  AND (icon_name IS NULL OR icon_name='');

-- 旧 category 表可能还有不属于正式小程序目录的历史行；保留为空字符串以满足列级 NOT NULL。
UPDATE category SET icon_name='' WHERE icon_name IS NULL;

SET @ddl = (SELECT IF(IS_NULLABLE = 'YES',
  'ALTER TABLE `category` MODIFY COLUMN `icon_name` varchar(64) NOT NULL COMMENT ''antd-mini 分类图标名称''',
  'SELECT 1') FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND COLUMN_NAME='icon_name');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
  'CREATE UNIQUE INDEX `uk_category_code` ON `category` (`code`)', 'SELECT 1')
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND INDEX_NAME='uk_category_code');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'CREATE INDEX `idx_category_parent_status_sort` ON `category` (`parent_code`,`status`,`sort_order`,`code`)', 'SELECT 1')
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='category' AND INDEX_NAME='idx_category_parent_status_sort');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'CREATE INDEX `idx_goods_public_category_sort` ON `goods` (`status`,`is_public`,`category_code`,`sort_order`,`goods_id`)', 'SELECT 1')
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='goods' AND INDEX_NAME='idx_goods_public_category_sort');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'CREATE INDEX `idx_goods_sku_goods_stock_rent` ON `goods_sku` (`goods_id`,`stock`,`daily_rent`)', 'SELECT 1')
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='goods_sku' AND INDEX_NAME='idx_goods_sku_goods_stock_rent');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 商品 query 会按 goods_id 汇总已付款及履约订单；避免公开目录请求扫描整张订单表。
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'CREATE INDEX `idx_rent_order_goods_id` ON `rent_order` (`goods_id`)', 'SELECT 1')
  FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@schema_name AND TABLE_NAME='rent_order' AND INDEX_NAME='idx_rent_order_goods_id');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS rent_catalog_goods_category_mapping (
  goods_id int NOT NULL,
  category_code varchar(64) NOT NULL,
  review_status varchar(16) NOT NULL DEFAULT 'PENDING',
  reviewer varchar(128) DEFAULT NULL,
  reviewed_at datetime DEFAULT NULL,
  applied_at datetime DEFAULT NULL,
  note varchar(512) DEFAULT NULL,
  PRIMARY KEY (goods_id),
  KEY idx_catalog_mapping_status (review_status, category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工核对的历史商品分类映射';

CREATE TABLE IF NOT EXISTS rent_catalog_admin_operation (
  id bigint NOT NULL AUTO_INCREMENT,
  idempotency_key varchar(128) DEFAULT NULL COMMENT '高风险操作幂等键',
  action varchar(32) NOT NULL,
  resource_code varchar(64) DEFAULT NULL,
  operator_name varchar(128) DEFAULT NULL,
  request_summary varchar(1024) DEFAULT NULL,
  result_summary varchar(1024) DEFAULT NULL,
  success tinyint NOT NULL DEFAULT 0,
  fail_reason varchar(512) DEFAULT NULL,
  created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_catalog_operation_idempotency (idempotency_key, action),
  KEY idx_catalog_operation_resource_time (resource_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自有小程序目录后台操作审计';

-- 迁移闸门基础校验：非 HTTPS 图片必须先修复；结果应为 0。
SELECT COUNT(*) AS invalid_public_cover_count
FROM goods WHERE status=1 AND is_public=1 AND (cover IS NULL OR cover NOT LIKE 'https://%');

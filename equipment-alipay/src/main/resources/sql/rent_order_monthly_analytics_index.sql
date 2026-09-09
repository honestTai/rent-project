-- 小程序个人中心月度租赁分析索引。
-- rent_order.created_at 为毫秒时间戳；月度查询固定使用 user_uuid + created_at 左闭右开范围。

SET @schema_name = DATABASE();

SET @index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'rent_order'
      AND index_name = 'idx_rent_order_user_uuid_created_at'
);
SET @ddl = IF(
    @index_exists = 0,
    'ALTER TABLE `rent_order` ADD INDEX `idx_rent_order_user_uuid_created_at` (`user_uuid`, `created_at`)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- renewOrderCount 使用子单 ID + 用户 UUID 做精确存在性判断，避免扫描续租关系表。
SET @relation_index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'rent_order_relet_relation'
      AND index_name = 'idx_relet_child_order_user_uuid'
);
SET @relation_ddl = IF(
    @relation_index_exists = 0,
    'ALTER TABLE `rent_order_relet_relation` ADD INDEX `idx_relet_child_order_user_uuid` (`child_order_id`, `user_uuid`)',
    'SELECT 1'
);
PREPARE relation_stmt FROM @relation_ddl;
EXECUTE relation_stmt;
DEALLOCATE PREPARE relation_stmt;

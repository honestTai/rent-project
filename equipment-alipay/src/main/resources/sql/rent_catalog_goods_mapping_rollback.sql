-- 数据回滚：恢复首次执行映射前的 category_code。结构列与分类种子保留，便于应用版本安全回滚。
START TRANSACTION;
UPDATE goods g
JOIN rent_catalog_goods_category_backup b ON b.goods_id=g.goods_id
SET g.category_code=b.category_code;
UPDATE rent_catalog_goods_category_mapping SET applied_at=NULL;
COMMIT;

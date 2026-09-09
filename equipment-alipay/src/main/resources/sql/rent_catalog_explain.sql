-- 在完成正式映射的预发布/生产只读连接上执行，确认 key 使用 idx_goods_public_category_sort
-- 和 idx_goods_sku_goods_stock_rent；本文件只读。
EXPLAIN FORMAT=JSON
SELECT g.*
FROM goods g
WHERE g.status=1
  AND g.is_public=1
  AND g.category_code IN ('drone-portable','drone-pro','drone-fpv','drone-industry')
  AND EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id=g.goods_id)
  AND EXISTS (SELECT 1 FROM category c WHERE c.code=g.category_code AND c.status=1)
ORDER BY g.sort_order DESC, g.goods_id ASC
LIMIT 0,20;

EXPLAIN FORMAT=JSON
SELECT g.category_code, COUNT(DISTINCT g.goods_id)
FROM goods g
WHERE g.status=1
  AND g.is_public=1
  AND g.category_code IN ('drone-portable','drone-pro','drone-fpv','drone-industry')
  AND EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id=g.goods_id)
GROUP BY g.category_code;

EXPLAIN FORMAT=JSON
SELECT g.*
FROM goods g
WHERE g.status=1
  AND g.is_public=1
  AND (g.title LIKE '%DJI%' OR g.activity_text LIKE '%DJI%' OR g.description LIKE '%DJI%'
    OR g.brand LIKE '%DJI%' OR g.model_name LIKE '%DJI%')
  AND EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id=g.goods_id AND s.stock>0)
ORDER BY g.sort_order DESC, g.goods_id ASC
LIMIT 0,20;

-- 注意：前置通配符关键词查询不会使用普通 B-Tree。数据量增长后，应单独评审 MySQL FULLTEXT
--（title/activity_text/description/brand/model_name）后再切换，不能在运行时降级为内存搜索。

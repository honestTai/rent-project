-- 仅应用 review_status=APPROVED 的“商品主键 -> 叶子分类编码”映射；可重复执行。
START TRANSACTION;

CREATE TABLE IF NOT EXISTS rent_catalog_goods_category_backup (
  goods_id int NOT NULL,
  category_code varchar(64) DEFAULT NULL,
  backed_up_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类迁移前值，仅用于受控回滚';

INSERT IGNORE INTO rent_catalog_goods_category_backup (goods_id, category_code)
SELECT g.goods_id, g.category_code FROM goods g;

UPDATE goods g
JOIN rent_catalog_goods_category_mapping m ON m.goods_id=g.goods_id
JOIN category c ON c.code=m.category_code AND c.status=1
LEFT JOIN category child ON child.parent_code=c.code
SET g.category_code=m.category_code,
    m.applied_at=NOW()
WHERE m.review_status='APPROVED' AND child.category_id IS NULL;

COMMIT;

-- 安全摘要，不包含用户数据、令牌或密钥。
SELECT
  (SELECT COUNT(*) FROM goods) AS total_goods,
  (SELECT COUNT(*) FROM rent_catalog_goods_category_mapping WHERE review_status='APPROVED') AS approved_mapping_count,
  (SELECT COUNT(*) FROM rent_catalog_goods_category_mapping WHERE applied_at IS NOT NULL) AS successfully_bound_count,
  (SELECT COUNT(*) FROM goods WHERE status=1 AND is_public=1 AND (category_code IS NULL OR category_code='')) AS unbound_public_count,
  (SELECT COUNT(*) FROM goods WHERE status=1 AND is_public=1 AND (cover IS NULL OR cover NOT LIKE 'https://%')) AS invalid_public_cover_count,
  (SELECT COUNT(*) FROM goods g WHERE status=1 AND is_public=1 AND (
    NOT EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id=g.goods_id) OR
    (g.default_rent_unit IS NOT NULL AND g.default_rent_unit NOT IN ('DAY','MONTH')) OR
    (g.default_rent_unit='DAY' AND NOT EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id=g.goods_id AND (s.billing_cycle IS NULL OR s.billing_cycle<=1))) OR
    (g.default_rent_unit='MONTH' AND NOT EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id=g.goods_id AND s.billing_cycle>1))
  )) AS invalid_product_fact_count,
  (SELECT COUNT(*) FROM rent_catalog_goods_category_mapping m
    LEFT JOIN goods g ON g.goods_id=m.goods_id
    LEFT JOIN category c ON c.code=m.category_code AND c.status=1
    WHERE m.review_status='APPROVED' AND (g.goods_id IS NULL OR c.category_id IS NULL
      OR EXISTS (SELECT 1 FROM category child WHERE child.parent_code=m.category_code))) AS conflict_count;

-- 待人工确认清单：只输出商品业务数据，不输出任何用户信息。
SELECT g.goods_id, g.alipay_goods_id, g.title
FROM goods g
WHERE g.status=1 AND g.is_public=1 AND (g.category_code IS NULL OR g.category_code='')
ORDER BY g.goods_id;

-- 发布闸门：只有 gate_status=PASS 才允许小程序切换分类流量。
SELECT IF(
  EXISTS (SELECT 1 FROM goods g WHERE g.status=1 AND g.is_public=1 AND (
    g.category_code IS NULL OR g.category_code='' OR
    g.cover IS NULL OR g.cover NOT LIKE 'https://%' OR
    NOT EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id=g.goods_id) OR
    (g.default_rent_unit IS NOT NULL AND g.default_rent_unit NOT IN ('DAY','MONTH')) OR
    (g.default_rent_unit='DAY' AND NOT EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id=g.goods_id AND (s.billing_cycle IS NULL OR s.billing_cycle<=1))) OR
    (g.default_rent_unit='MONTH' AND NOT EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id=g.goods_id AND s.billing_cycle>1)) OR
    NOT EXISTS (SELECT 1 FROM category c WHERE c.code=g.category_code AND c.status=1) OR
    EXISTS (SELECT 1 FROM category child WHERE child.parent_code=g.category_code)
  )), 'FAIL', 'PASS') AS gate_status;

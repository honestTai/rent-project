-- 支付宝租赁后台菜单与按钮权限增量脚本
-- 用途：补齐当前 rent-tdesign 前端路由对应的 RBAC 菜单、按钮、接口规则和内置角色默认权限。
-- 说明：系统码表已统一迁移到中台配置中心，支付宝租赁后台不再保留 alipay:config 菜单。

START TRANSACTION;

INSERT INTO platform_rbac_system(system_code, system_name, sort, enabled, created_at, updated_at)
VALUES ('alipay', '支付宝租赁', 30, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  system_name = VALUES(system_name),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_page(system_code, page_code, page_name, route_path, sort, enabled, created_at, updated_at)
VALUES
  ('alipay', 'dashboard', '经营看板', '/alipay/dashboard', 10, 1, NOW(), NOW()),
  ('alipay', 'analysis', '数据分析', '/alipay/analysis', 20, 1, NOW(), NOW()),
  ('alipay', 'report', '报表中心', '/alipay/reports', 30, 1, NOW(), NOW()),
  ('alipay', 'order', '订单列表', '/alipay/orders', 40, 1, NOW(), NOW()),
  ('alipay', 'aftersale', '售后同步', '/alipay/aftersales', 50, 1, NOW(), NOW()),
  ('alipay', 'goods', '设备', '/alipay/goods', 60, 1, NOW(), NOW()),
  ('alipay', 'banner', '轮播图设置', '/alipay/banners', 65, 1, NOW(), NOW()),
  ('alipay', 'catalog', '小程序目录', '/alipay/catalog', 66, 1, NOW(), NOW()),
  ('alipay', 'user', '小程序用户', '/alipay/users', 70, 1, NOW(), NOW()),
  ('alipay', 'ledger', '台账管理', '/alipay/ledger', 80, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  page_name = VALUES(page_name),
  route_path = VALUES(route_path),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_button(system_code, page_code, button_code, button_name, api_method, api_path, sort, enabled, created_at, updated_at)
VALUES
  ('alipay', 'dashboard', 'view', '查看看板', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'dashboard', 'export', '导出看板', NULL, NULL, 20, 1, NOW(), NOW()),
  ('alipay', 'analysis', 'view', '查看分析', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'analysis', 'export', '导出分析', NULL, NULL, 20, 1, NOW(), NOW()),
  ('alipay', 'report', 'view', '查看报表', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'report', 'export', '导出报表', NULL, NULL, 20, 1, NOW(), NOW()),
  ('alipay', 'report', 'generate', '生成报表', NULL, NULL, 30, 1, NOW(), NOW()),
  ('alipay', 'order', 'view', '查看订单', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'order', 'operate', '订单操作', NULL, NULL, 20, 1, NOW(), NOW()),
  ('alipay', 'order', 'export', '导出订单', NULL, NULL, 30, 1, NOW(), NOW()),
  ('alipay', 'aftersale', 'view', '查看售后同步', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'aftersale', 'sync', '扫描支付宝售后', NULL, NULL, 20, 1, NOW(), NOW()),
  ('alipay', 'aftersale', 'import', '导入售后台账', NULL, NULL, 30, 1, NOW(), NOW()),
  ('alipay', 'goods', 'view', '查看商品', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'goods', 'sync', '同步商品', NULL, NULL, 20, 1, NOW(), NOW()),
  ('alipay', 'goods', 'create', '新增商品', NULL, NULL, 30, 1, NOW(), NOW()),
  ('alipay', 'goods', 'update', '编辑商品', NULL, NULL, 40, 1, NOW(), NOW()),
  ('alipay', 'goods', 'delete', '删除商品', NULL, NULL, 50, 1, NOW(), NOW()),
  ('alipay', 'goods', 'export', '导出商品', NULL, NULL, 60, 1, NOW(), NOW()),
  ('alipay', 'banner', 'view', '查看轮播图', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'banner', 'create', '新增轮播图', NULL, NULL, 20, 1, NOW(), NOW()),
  ('alipay', 'banner', 'update', '编辑轮播图', NULL, NULL, 30, 1, NOW(), NOW()),
  ('alipay', 'banner', 'delete', '删除轮播图', NULL, NULL, 40, 1, NOW(), NOW()),
  ('alipay', 'catalog', 'view', '查看小程序目录', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'catalog', 'create', '新增小程序分类', NULL, NULL, 20, 1, NOW(), NOW()),
  ('alipay', 'catalog', 'update', '编辑及启停分类', NULL, NULL, 30, 1, NOW(), NOW()),
  ('alipay', 'catalog', 'delete', '删除小程序分类', NULL, NULL, 40, 1, NOW(), NOW()),
  ('alipay', 'catalog', 'bind', '绑定租赁商品', NULL, NULL, 50, 1, NOW(), NOW()),
  ('alipay', 'user', 'view', '查看用户', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'user', 'update', '编辑用户', NULL, NULL, 20, 1, NOW(), NOW()),
  ('alipay', 'ledger', 'view', '查看台账', NULL, NULL, 10, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  button_name = VALUES(button_name),
  api_method = VALUES(api_method),
  api_path = VALUES(api_path),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/analytics/dashboard', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'dashboard' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/analytics/**', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'analysis' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/report-center/page', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'report' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'GET', '/api/web/report-center/*', 11, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'report' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/report-center/generate', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'report' AND button_code = 'generate'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/rent-component/page', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'order' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/rent-component/detail', 11, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'order' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/rent-component/return-record', 12, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'order' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/rent-component/risk-detail', 13, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'order' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/rent-component/deposit/query', 14, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'order' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/rent-component/deposit/deduct-records', 15, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'order' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/rent-component/refund' api_path, 20 sort UNION ALL
  SELECT '/api/web/rent-component/merchant-confirm', 21 UNION ALL
  SELECT '/api/web/rent-component/send', 22 UNION ALL
  SELECT '/api/web/rent-component/confirm-send', 23 UNION ALL
  SELECT '/api/web/rent-component/complete', 24 UNION ALL
  SELECT '/api/web/rent-component/sync', 25 UNION ALL
  SELECT '/api/web/rent-component/close', 26 UNION ALL
  SELECT '/api/web/rent-component/remark', 27 UNION ALL
  SELECT '/api/web/rent-component/deposit/deduct', 28 UNION ALL
  SELECT '/api/web/rent-component/deposit/deduct/confirm', 29 UNION ALL
  SELECT '/api/web/rent-component/identity-photo-review', 30 UNION ALL
  SELECT '/api/web/rent-component/contract/generate', 31
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'order' AND b.button_code = 'operate'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/rent-component/aftersale/scan' api_path, 10 sort UNION ALL
  SELECT '/api/web/rent-component/aftersale/preview', 11
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'aftersale' AND b.button_code IN ('view', 'sync')
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/rent-component/aftersale/import', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'aftersale' AND button_code = 'import'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/goods/page' api_path, 10 sort UNION ALL
  SELECT '/api/web/goods/**/page', 11 UNION ALL
  SELECT '/api/web/goods/detail', 12 UNION ALL
  SELECT '/api/web/goods/classifications/list', 13 UNION ALL
  SELECT '/api/web/goods/alipay-categories/query', 14 UNION ALL
  SELECT '/api/web/goods/alipay-rent-categories/query', 15
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'goods' AND b.button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/goods/create' api_path, 20 sort UNION ALL
  SELECT '/api/web/goods/attrs/create', 21 UNION ALL
  SELECT '/api/web/goods/alipay-categories/query', 22 UNION ALL
  SELECT '/api/web/goods/alipay-rent-categories/query', 23
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'goods' AND b.button_code = 'create'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/goods/update' api_path, 30 sort UNION ALL
  SELECT '/api/web/goods/attrs/update', 31 UNION ALL
  SELECT '/api/web/goods/up', 32 UNION ALL
  SELECT '/api/web/goods/down', 33 UNION ALL
  SELECT '/api/web/goods/sort-top', 34 UNION ALL
  SELECT '/api/web/goods/public-status/update', 35 UNION ALL
  SELECT '/api/web/goods/alipay-categories/query', 36 UNION ALL
  SELECT '/api/web/goods/alipay-rent-categories/query', 37
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'goods' AND b.button_code = 'update'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/goods/delete' api_path, 40 sort UNION ALL
  SELECT '/api/web/goods/attrs/delete', 41
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'goods' AND b.button_code = 'delete'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/goods/sync**', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'goods' AND button_code = 'sync'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/goods/alipay-categories/query', 50, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'platform' AND page_code = 'config' AND button_code = 'manage'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/goods/alipay-rent-categories/query', 51, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'platform' AND page_code = 'config' AND button_code = 'manage'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/miniapp-banners/page', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'banner' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/miniapp-banners/create', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'banner' AND button_code = 'create'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/miniapp-banners/update' api_path, 10 sort UNION ALL
  SELECT '/api/web/miniapp-banners/status/update', 11 UNION ALL
  SELECT '/api/web/miniapp-banners/sort-top', 12
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'banner' AND b.button_code = 'update'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/miniapp-banners/delete', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'banner' AND button_code = 'delete'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/catalog/categories/list' api_path, 10 sort UNION ALL
  SELECT '/api/web/catalog/categories/status-impact', 11 UNION ALL
  SELECT '/api/web/catalog/categories/goods/unclassified/page', 12
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'catalog' AND b.button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/catalog/categories/create', 20, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'catalog' AND button_code = 'create'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/catalog/categories/update' api_path, 30 sort UNION ALL
  SELECT '/api/web/catalog/categories/status/update', 31
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'catalog' AND b.button_code = 'update'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/catalog/categories/delete', 40, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'catalog' AND button_code = 'delete'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/catalog/categories/goods/bind', 50, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'catalog' AND button_code = 'bind'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/users/page' api_path, 10 sort UNION ALL
  SELECT '/api/web/roles/list', 11
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'user' AND b.button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/users/request-status/update', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'user' AND button_code = 'update'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/web/order-oper-logs/**', 10, 1, NOW(), NOW()
FROM platform_rbac_button WHERE system_code = 'alipay' AND page_code = 'ledger' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT IGNORE INTO platform_role_permission(role_id, button_id, created_at)
SELECT r.id, b.id, NOW()
FROM platform_role r
JOIN platform_rbac_button b ON b.system_code = 'alipay' AND b.enabled = 1
WHERE r.role_code = 'super_admin' AND r.enabled = 1;

INSERT IGNORE INTO platform_role_permission(role_id, button_id, created_at)
SELECT r.id, b.id, NOW()
FROM platform_role r
JOIN platform_rbac_button b ON b.system_code = 'alipay' AND b.enabled = 1 AND b.button_code = 'view'
WHERE r.role_code = 'readonly_operator' AND r.enabled = 1;

DELETE rp
FROM platform_role_permission rp
JOIN platform_rbac_button b ON b.id = rp.button_id
WHERE b.system_code = 'alipay' AND b.page_code = 'config';

UPDATE platform_rbac_api_rule ar
JOIN platform_rbac_button b ON b.id = ar.button_id
SET ar.enabled = 0,
    ar.updated_at = NOW()
WHERE b.system_code = 'alipay' AND b.page_code = 'config';

UPDATE platform_rbac_button
SET enabled = 0,
    updated_at = NOW()
WHERE system_code = 'alipay' AND page_code = 'config';

UPDATE platform_rbac_page
SET enabled = 0,
    updated_at = NOW()
WHERE system_code = 'alipay' AND page_code = 'config';

-- Public functional guide: a read-only frontend page, with no business write API.
INSERT INTO platform_rbac_page(system_code,page_code,page_name,route_path,sort,enabled,created_at,updated_at)
VALUES ('alipay','deliveryShowcase','功能导览','/alipay/delivery-showcase',15,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE page_name=VALUES(page_name),route_path=VALUES(route_path),sort=VALUES(sort),updated_at=NOW();
INSERT INTO platform_rbac_button(system_code,page_code,button_code,button_name,sort,enabled,created_at,updated_at)
VALUES ('alipay','deliveryShowcase','view','查看功能导览',10,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE button_name=VALUES(button_name),sort=VALUES(sort),updated_at=NOW();
INSERT IGNORE INTO platform_role_permission(role_id,button_id,created_at)
SELECT r.id,b.id,NOW() FROM platform_role r JOIN platform_rbac_button b
  ON b.system_code='alipay' AND b.page_code='deliveryShowcase' AND b.button_code='view'
WHERE r.role_code IN ('super_admin','readonly_operator') AND r.enabled=1;

COMMIT;

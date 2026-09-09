-- 支付宝租赁后台系统监控与系统日志 RBAC 增量脚本
-- 用途：只补齐本轮新增的 monitor/systemLog 页面、按钮、接口规则和内置角色默认授权。

START TRANSACTION;

INSERT INTO platform_rbac_system(system_code, system_name, sort, enabled, created_at, updated_at)
VALUES
  ('alipay', '支付宝租赁', 30, 1, NOW(), NOW()),
  ('platform', '产品中台', 10, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  system_name = VALUES(system_name),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_page(system_code, page_code, page_name, route_path, sort, enabled, created_at, updated_at)
VALUES
  ('alipay', 'monitor', '系统监控', '/alipay/monitor', 90, 1, NOW(), NOW()),
  ('alipay', 'systemLog', '系统日志', '/alipay/system-logs', 100, 1, NOW(), NOW()),
  ('platform', 'monitor', '系统监控', '/platform/monitor', 50, 1, NOW(), NOW()),
  ('platform', 'systemLog', '系统日志', '/platform/logs', 60, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  page_name = VALUES(page_name),
  route_path = VALUES(route_path),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_button(system_code, page_code, button_code, button_name, api_method, api_path, sort, enabled, created_at, updated_at)
VALUES
  ('alipay', 'monitor', 'view', '查看系统监控', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'systemLog', 'view', '查看系统日志', NULL, NULL, 10, 1, NOW(), NOW()),
  ('platform', 'monitor', 'view', '查看系统监控', NULL, NULL, 10, 1, NOW(), NOW()),
  ('platform', 'systemLog', 'view', '查看系统日志', NULL, NULL, 10, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  button_name = VALUES(button_name),
  api_method = VALUES(api_method),
  api_path = VALUES(api_path),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'GET', '/api/platform/monitor/overview', 10, 1, NOW(), NOW()
FROM platform_rbac_button
WHERE system_code = 'alipay' AND page_code = 'monitor' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'GET', '/api/platform/system-log/sources', 10, 1, NOW(), NOW()
FROM platform_rbac_button
WHERE system_code = 'alipay' AND page_code = 'systemLog' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/platform/system-log/query', 11, 1, NOW(), NOW()
FROM platform_rbac_button
WHERE system_code = 'alipay' AND page_code = 'systemLog' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'GET', '/api/platform/monitor/overview', 10, 1, NOW(), NOW()
FROM platform_rbac_button
WHERE system_code = 'platform' AND page_code = 'monitor' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'GET', '/api/platform/system-log/sources', 10, 1, NOW(), NOW()
FROM platform_rbac_button
WHERE system_code = 'platform' AND page_code = 'systemLog' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/platform/system-log/query', 11, 1, NOW(), NOW()
FROM platform_rbac_button
WHERE system_code = 'platform' AND page_code = 'systemLog' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT IGNORE INTO platform_role_permission(role_id, button_id, created_at)
SELECT r.id, b.id, NOW()
FROM platform_role r
JOIN platform_rbac_button b ON b.enabled = 1
  AND b.system_code IN ('alipay', 'platform')
  AND b.page_code IN ('monitor', 'systemLog')
WHERE r.role_code = 'super_admin' AND r.enabled = 1;

INSERT IGNORE INTO platform_role_permission(role_id, button_id, created_at)
SELECT r.id, b.id, NOW()
FROM platform_role r
JOIN platform_rbac_button b ON b.enabled = 1
  AND b.system_code IN ('alipay', 'platform')
  AND b.page_code IN ('monitor', 'systemLog')
  AND b.button_code = 'view'
WHERE r.role_code = 'readonly_operator' AND r.enabled = 1;

COMMIT;

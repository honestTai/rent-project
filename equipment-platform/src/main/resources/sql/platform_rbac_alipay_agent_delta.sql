-- 业务后台 AI 助手 RBAC 增量脚本
-- 用途：仅为本开源仓库的支付宝租赁后台新增只读 AI 助手权限；不创建其他分支的系统。

START TRANSACTION;

INSERT INTO platform_rbac_system(system_code, system_name, sort, enabled, created_at, updated_at)
VALUES
  ('alipay', '支付宝租赁', 30, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  system_name = VALUES(system_name),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_page(system_code, page_code, page_name, route_path, sort, enabled, created_at, updated_at)
VALUES
  ('alipay', 'agent', 'AI 助手浮窗', '/alipay/dashboard', 110, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  page_name = VALUES(page_name),
  route_path = VALUES(route_path),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_button(system_code, page_code, button_code, button_name, api_method, api_path, sort, enabled, created_at, updated_at)
VALUES
  ('alipay', 'agent', 'view', '查看 AI 助手', NULL, NULL, 10, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  button_name = VALUES(button_name),
  api_method = VALUES(api_method),
  api_path = VALUES(api_path),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'POST', '/api/agent/chat', 10, 1, NOW(), NOW()
FROM platform_rbac_button
WHERE system_code = 'alipay' AND page_code = 'agent' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT id, 'GET', '/api/agent/**', 11, 1, NOW(), NOW()
FROM platform_rbac_button
WHERE system_code = 'alipay' AND page_code = 'agent' AND button_code = 'view'
ON DUPLICATE KEY UPDATE sort = VALUES(sort), enabled = VALUES(enabled), updated_at = NOW();

INSERT IGNORE INTO platform_role_permission(role_id, button_id, created_at)
SELECT r.id, b.id, NOW()
FROM platform_role r
JOIN platform_rbac_button b ON b.system_code = 'alipay'
  AND b.page_code = 'agent'
  AND b.button_code = 'view'
  AND b.enabled = 1
WHERE r.role_code IN ('super_admin', 'readonly_operator') AND r.enabled = 1;

COMMIT;

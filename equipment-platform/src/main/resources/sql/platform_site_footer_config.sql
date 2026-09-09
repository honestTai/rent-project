-- 全站底部备案信息配置
-- 已有值不覆盖，客户上线前可在中台配置中心替换为真实备案号与企业名称。

SET NAMES utf8mb4;

INSERT INTO platform_config(system_code, config_group, config_key, config_value, value_type, secret_flag, enabled, display_name, remark, sort, created_at, updated_at)
VALUES
('common', 'site', 'site.footer.icp-record', '演示环境 · 暂无备案', 'string', 0, 1, 'ICP备案号', '展示在所有后台页面底部，请替换为真实 ICP 备案号', 700, NOW(), NOW()),
('common', 'site', 'site.footer.police-record', '', 'string', 0, 1, '公安备案号', '展示在所有后台页面底部，请替换为真实公安备案号', 710, NOW(), NOW()),
('common', 'site', 'site.footer.copyright', 'Copyright © 2026 HONESTTAI', 'string', 0, 1, '版权信息', '展示在所有后台页面底部，请替换为真实企业名称', 720, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  config_group = VALUES(config_group),
  config_value = IF(platform_config.config_value IS NULL OR platform_config.config_value = '', VALUES(config_value), platform_config.config_value),
  value_type = VALUES(value_type),
  secret_flag = VALUES(secret_flag),
  enabled = VALUES(enabled),
  display_name = VALUES(display_name),
  remark = VALUES(remark),
  sort = VALUES(sort),
  updated_at = VALUES(updated_at);

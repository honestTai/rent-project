-- Agent 模型配置初始化脚本。
-- 执行后可在中台「配置中心」按系统 platform、分组 agent 搜索维护。

START TRANSACTION;

INSERT INTO platform_config
(system_code, config_group, config_key, config_value, value_type, secret_flag, enabled, display_name, remark, sort, created_at, updated_at)
VALUES
  ('platform', 'agent', 'agent.llm.provider', 'ds', 'string', 0, 1, 'Agent 模型供应商', '默认 ds；也可填 openai 并配置 OpenAI 兼容地址', 10, NOW(), NOW()),
  ('platform', 'agent', 'agent.llm.model', 'deepseek-chat', 'string', 0, 1, 'Agent 模型名称', 'DeepSeek 默认模型，可按模型中台实际名称调整', 20, NOW(), NOW()),
  ('platform', 'agent', 'agent.llm.base-url', 'https://api.deepseek.com', 'url', 0, 1, 'Agent 模型 Base URL', 'OpenAI 兼容接口地址，例如 DeepSeek 或模型中台地址', 30, NOW(), NOW()),
  ('platform', 'agent', 'agent.llm.api-key', '', 'secret', 1, 1, 'Agent 模型 API Key', '敏感配置；填写后 agent 才会调用模型，留空时走只读规则回复', 40, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  config_group = VALUES(config_group),
  config_value = CASE
    WHEN platform_config.config_key = 'agent.llm.api-key'
      AND platform_config.config_value IS NOT NULL
      AND platform_config.config_value <> ''
      THEN platform_config.config_value
    ELSE VALUES(config_value)
  END,
  value_type = VALUES(value_type),
  secret_flag = VALUES(secret_flag),
  enabled = VALUES(enabled),
  display_name = VALUES(display_name),
  remark = VALUES(remark),
  sort = VALUES(sort),
  updated_at = NOW();

COMMIT;

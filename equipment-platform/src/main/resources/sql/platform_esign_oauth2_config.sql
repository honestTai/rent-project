-- e签宝 OAuth2 直连接入配置模板。
-- 说明：
-- 1. 本脚本不写入 AppSecret；请在生产/沙箱环境通过 platform_config 单独写入 esign.app-secret。
-- 2. 沙箱验证使用 https://smlopenapi.esign.cn + env=sml；正式上线改为 https://openapi.esign.cn + env=prod。
-- 3. 默认只创建用户手动签署区，满足“下单后用户可直接签署”的最短真实链路。

SET NAMES utf8mb4;

INSERT INTO platform_config
  (system_code, config_group, config_key, config_value, value_type, secret_flag, enabled, display_name, remark, sort, created_at, updated_at)
VALUES
  ('alipay', 'esign', 'esign.auth-mode', 'oauth2', 'string', 0, 1, 'e签宝鉴权方式', 'oauth2=后端使用 AppID/AppSecret 自动获取 OAuthToken；留空则兼容旧签署网关', 548, NOW(), NOW()),
  ('alipay', 'esign', 'esign.app-secret', '', 'secret', 1, 1, 'e签宝 AppSecret', 'e签宝开放平台应用密钥，只允许写入配置中心/数据库，不允许写入代码', 553, NOW(), NOW()),
  ('alipay', 'esign', 'esign.auto-archive', 'false', 'boolean', 0, 1, '签署后自动归档', '新环境默认停用；完成电子签接入验证后由中台按需启用，已有配置保留', 567, NOW(), NOW()),
  ('alipay', 'esign', 'esign.sign-platform', '2', 'string', 0, 1, 'e签宝签署平台', '2=支付宝签，用于支付宝小程序唤起 e签宝签署', 568, NOW(), NOW()),
  ('alipay', 'esign', 'esign.notice-type', '', 'string', 0, 1, 'e签宝通知方式', '逗号分隔：1短信、2邮件、3支付宝、4钉钉；留空时不额外通知，由小程序跳转签署', 569, NOW(), NOW()),
  ('alipay', 'esign', 'esign.account.third-party-prefix', 'rent-user-', 'string', 0, 1, 'e签宝账号唯一前缀', '创建个人签署账号时拼接本地 userUuid，避免和其他系统 thirdPartyUserId 冲突', 570, NOW(), NOW()),
  ('alipay', 'esign', 'esign.signer.id-type', 'CRED_PSN_CH_IDCARD', 'string', 0, 1, '签署人证件类型', '默认中国大陆居民身份证', 571, NOW(), NOW()),
  ('alipay', 'esign', 'esign.signer.order', '1', 'number', 0, 1, '用户签署顺序', '未启用平台自动盖章时默认为 1；若启用企业自动盖章可改为 2', 572, NOW(), NOW()),
  ('alipay', 'esign', 'esign.signer.seal-type', '0', 'string', 0, 1, '用户签署方式', '0=手绘签名', 573, NOW(), NOW()),
  ('alipay', 'esign', 'esign.signer.sign-type', '0', 'number', 0, 1, '用户签署类型', '0=不限签署位置；快速接入时由用户在 e签宝内完成签署确认', 574, NOW(), NOW()),
  ('alipay', 'esign', 'esign.signer.assigned-pos', 'false', 'boolean', 0, 1, '固定用户签署位置', 'false=不固定签署坐标；需要固定落章位置时改为 true 并配置 pos-page/pos-x/pos-y', 575, NOW(), NOW()),
  ('alipay', 'esign', 'esign.signer.pos-page', '1', 'string', 0, 1, '用户签署页码', '固定用户签署位置时使用', 576, NOW(), NOW()),
  ('alipay', 'esign', 'esign.signer.pos-x', '420', 'number', 0, 1, '用户签署 X 坐标', '固定用户签署位置时使用', 577, NOW(), NOW()),
  ('alipay', 'esign', 'esign.signer.pos-y', '680', 'number', 0, 1, '用户签署 Y 坐标', '固定用户签署位置时使用', 578, NOW(), NOW()),
  ('alipay', 'esign', 'esign.platform-sign.enabled', 'false', 'boolean', 0, 1, '企业自动盖章开关', '快速接入默认关闭；正式合同需要企业章时打开并确保 e签宝后台有默认企业章或配置 sealId', 579, NOW(), NOW()),
  ('alipay', 'esign', 'esign.seal-id', '', 'secret', 1, 1, '企业印章 sealId', '留空时 e签宝使用默认企业章；指定印章时填 e签宝 sealId', 580, NOW(), NOW()),
  ('alipay', 'esign', 'esign.platform-sign.order', '1', 'number', 0, 1, '企业盖章顺序', '启用企业自动盖章时使用', 581, NOW(), NOW()),
  ('alipay', 'esign', 'esign.platform-sign.sign-type', '1', 'number', 0, 1, '企业盖章签署类型', '1=单页签署', 582, NOW(), NOW()),
  ('alipay', 'esign', 'esign.platform-sign.pos-page', '1', 'string', 0, 1, '企业盖章页码', '启用企业自动盖章时使用', 583, NOW(), NOW()),
  ('alipay', 'esign', 'esign.platform-sign.pos-x', '420', 'number', 0, 1, '企业盖章 X 坐标', '启用企业自动盖章时使用', 584, NOW(), NOW()),
  ('alipay', 'esign', 'esign.platform-sign.pos-y', '620', 'number', 0, 1, '企业盖章 Y 坐标', '启用企业自动盖章时使用', 585, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  config_group = VALUES(config_group),
  config_value = IF(platform_config.config_value IS NULL OR platform_config.config_value = '', VALUES(config_value), platform_config.config_value),
  value_type = VALUES(value_type),
  secret_flag = VALUES(secret_flag),
  enabled = platform_config.enabled,
  display_name = VALUES(display_name),
  remark = VALUES(remark),
  sort = VALUES(sort),
  updated_at = NOW();

UPDATE platform_config
SET config_value = 'https://smlopenapi.esign.cn',
    updated_at = NOW()
WHERE system_code = 'alipay'
  AND config_key = 'esign.base-url'
  AND (config_value IS NULL OR config_value = '' OR config_value LIKE '%example.com%');

UPDATE platform_config
SET config_value = 'sml',
    updated_at = NOW()
WHERE system_code = 'alipay'
  AND config_key = 'esign.miniapp.env'
  AND (config_value IS NULL OR config_value = '' OR config_value = 'prod');

UPDATE platform_config
SET config_value = 'plugin',
    updated_at = NOW()
WHERE system_code = 'alipay'
  AND config_key = 'esign.miniapp.open-type'
  AND (config_value IS NULL OR config_value = '' OR config_value = 'miniProgram');

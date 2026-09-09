-- 中台库：支付宝租赁后台电子合同、分期账单、自动扣款签约权限干净脚本
-- 说明：只写 RBAC 元数据，不包含 dev 环境业务数据，可重复执行。

SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO platform_rbac_system(system_code, system_name, sort, enabled, created_at, updated_at)
VALUES ('alipay', '支付宝租赁', 30, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  system_name = VALUES(system_name),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_page(system_code, page_code, page_name, route_path, sort, enabled, created_at, updated_at)
VALUES ('alipay', 'order', '订单列表', '/alipay/orders', 40, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  page_name = VALUES(page_name),
  route_path = VALUES(route_path),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_button(system_code, page_code, button_code, button_name, api_method, api_path, sort, enabled, created_at, updated_at)
VALUES
  ('alipay', 'order', 'view', '查看订单', NULL, NULL, 10, 1, NOW(), NOW()),
  ('alipay', 'order', 'operate', '订单操作', NULL, NULL, 20, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  button_name = VALUES(button_name),
  api_method = VALUES(api_method),
  api_path = VALUES(api_path),
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/rent-component/esign-contract' api_path, 40 sort UNION ALL
  SELECT '/api/web/rent-component/installment-bills', 41
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'order' AND b.button_code = 'view'
ON DUPLICATE KEY UPDATE
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT INTO platform_rbac_api_rule(button_id, api_method, api_path, sort, enabled, created_at, updated_at)
SELECT b.id, 'POST', paths.api_path, paths.sort, 1, NOW(), NOW()
FROM platform_rbac_button b
JOIN (
  SELECT '/api/web/rent-component/esign-contract/sign' api_path, 42 sort UNION ALL
  SELECT '/api/web/rent-component/withhold/sign', 43
) paths ON 1 = 1
WHERE b.system_code = 'alipay' AND b.page_code = 'order' AND b.button_code = 'operate'
ON DUPLICATE KEY UPDATE
  sort = VALUES(sort),
  enabled = VALUES(enabled),
  updated_at = NOW();

INSERT IGNORE INTO platform_role_permission(role_id, button_id, created_at)
SELECT r.id, b.id, NOW()
FROM platform_role r
JOIN platform_rbac_button b ON b.system_code = 'alipay'
  AND b.page_code = 'order'
  AND b.button_code IN ('view', 'operate')
  AND b.enabled = 1
WHERE r.role_code = 'super_admin' AND r.enabled = 1;

INSERT IGNORE INTO platform_role_permission(role_id, button_id, created_at)
SELECT r.id, b.id, NOW()
FROM platform_role r
JOIN platform_rbac_button b ON b.system_code = 'alipay'
  AND b.page_code = 'order'
  AND b.button_code = 'view'
  AND b.enabled = 1
WHERE r.role_code = 'readonly_operator' AND r.enabled = 1;

INSERT INTO platform_config(system_code, config_group, config_key, config_value, value_type, secret_flag, enabled, display_name, remark, sort, created_at, updated_at)
VALUES
  ('alipay', 'billing', 'billing.alipay.notify-url', '', 'url', 0, 1, '账单支付通知地址', '支付宝普通账单支付回调地址，需指向 /notify/alipay 或网关统一通知入口', 520, NOW(), NOW()),
  ('alipay', 'billing', 'billing.alipay.product-code', 'JSAPI_PAY', 'string', 0, 1, '账单支付产品码', '小程序内分期账单支付使用 JSAPI_PAY', 521, NOW(), NOW()),
  ('alipay', 'billing', 'billing.alipay.op-app-id', '', 'secret', 1, 1, '账单支付小程序 AppId', 'alipay.trade.create JSAPI_PAY 使用的 op_app_id，留空时后端回退 alipay.trade-app-id', 522, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.sign-entry.enabled', 'false', 'boolean', 0, 1, '自动代扣签约入口开关', '新环境默认停用；完成支付宝接入验证后由中台按需启用', 529, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.scheduler.enabled', 'false', 'boolean', 0, 1, '自动扣款任务开关', '新环境默认停用；核验签约与账单后由中台明确启用', 530, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.alipay.notify-url', '', 'url', 0, 1, '自动扣款签约通知地址', 'alipay.user.agreement.page.sign 回调地址', 531, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.alipay.deduct-notify-url', '', 'url', 0, 1, '自动扣款支付通知地址', 'alipay.trade.pay 自动扣款回调地址，留空时回退 billing.alipay.notify-url', 532, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.alipay.sign-scene', 'INDUSTRY|DIGITAL_MEDIA', 'string', 0, 1, '自动扣款签约场景', '支付宝周期扣款签约 sign_scene', 533, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.alipay.product-code', 'GENERAL_WITHHOLDING', 'string', 0, 1, '自动扣款签约产品码', '支付宝代扣签约 product_code', 534, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.alipay.personal-product-code', 'CYCLE_PAY_AUTH_P', 'string', 0, 1, '个人代扣产品码', '支付宝周期扣款个人产品码', 535, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.alipay.access-channel', 'ALIPAYAPP', 'string', 0, 1, '签约接入渠道', '小程序内签约使用 ALIPAYAPP，并由 my.paySignCenter 唤起', 536, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.alipay.period-type', 'DAY', 'string', 0, 1, '扣款周期类型', '支付宝 period_rule_params.period_type', 537, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.alipay.trade-product-code', 'GENERAL_WITHHOLDING', 'string', 0, 1, '自动扣款交易产品码', 'alipay.trade.pay 自动扣款 product_code', 538, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.scheduler.start-hour', '7', 'number', 0, 1, '自动扣款开始小时', '北京时间，默认 7 点开始发起自动扣款', 539, NOW(), NOW()),
  ('alipay', 'withhold', 'withhold.scheduler.end-hour', '22', 'number', 0, 1, '自动扣款结束小时', '北京时间，默认 22 点后停止发起自动扣款', 540, NOW(), NOW()),
  ('alipay', 'esign', 'esign.enabled', 'false', 'boolean', 0, 1, '是否开启电子合同', '开启后用户确认收货前必须完成电子合同签署；关闭时仅展示默认 PDF 协议', 549, NOW(), NOW()),
  ('alipay', 'esign', 'esign.base-url', '', 'url', 0, 1, '签署网关接口基础地址', '签署网关 API base-url', 550, NOW(), NOW()),
  ('alipay', 'esign', 'esign.auth-token', '', 'secret', 1, 1, '签署网关授权令牌', '调用签署网关接口使用的 Authorization', 551, NOW(), NOW()),
  ('alipay', 'esign', 'esign.app-id', '', 'secret', 1, 1, '签署网关 AppId', '可选，透传到 X-Tsign-Open-App-Id', 552, NOW(), NOW()),
  ('alipay', 'esign', 'esign.upload-path', '', 'string', 0, 1, '合同上传文件路径', '上传合同 PDF 的接口路径', 553, NOW(), NOW()),
  ('alipay', 'esign', 'esign.sign-flow-path', '', 'string', 0, 1, '创建签署流程路径', '创建签署流程接口路径', 554, NOW(), NOW()),
  ('alipay', 'esign', 'esign.query-flow-path', '', 'string', 0, 1, '查询签署流程路径', '支持 {flowId} 占位符', 555, NOW(), NOW()),
  ('alipay', 'esign', 'esign.download-path', '', 'string', 0, 1, '下载签署文件路径', '支持 {flowId} 占位符', 556, NOW(), NOW()),
  ('alipay', 'esign', 'esign.notify-url', '', 'url', 0, 1, '签署回调通知地址', '创建签署流程时传给签署网关的回调地址', 557, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.app-id', '2019042964339413', 'string', 0, 1, '签署小程序 AppId', '前端跳转签署小程序用', 558, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.env', 'prod', 'string', 0, 1, '签署小程序环境', 'prod/test', 559, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.path', 'pages/startup/index', 'string', 0, 1, '签署小程序启动页', 'e签宝支付宝小程序固定启动页 pages/startup/index', 560, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.page', 'sign', 'string', 0, 1, '签署小程序签署页', 'e签宝支付宝小程序 extraData.query.page，默认 sign', 561, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.forward-home', 'true', 'boolean', 0, 1, '签署完成引导首页', 'e签宝支付宝小程序 forwardHome 参数，默认 true', 562, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.open-type', 'miniProgram', 'string', 0, 1, '签署小程序打开方式', '默认 miniProgram，按 e签宝支付宝小程序官方跳转方式打开', 563, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.plugin-page', 'esign', 'string', 0, 1, '签署插件页', '兼容旧 e签宝插件入口', 564, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.skip-result', 'false', 'boolean', 0, 1, '签署跳过结果页', '兼容旧插件入口，签署完成后是否跳过结果页', 565, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.skip-guide', 'false', 'boolean', 0, 1, '签署跳过引导页', '兼容旧插件入口，签署前是否跳过引导页', 566, NOW(), NOW())
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
SET config_value = 'JSAPI_PAY',
    remark = '小程序内分期账单支付使用 JSAPI_PAY',
    updated_at = NOW()
WHERE system_code = 'alipay'
  AND config_key = 'billing.alipay.product-code'
  AND (config_value IS NULL OR config_value = '' OR config_value = 'FACE_TO_FACE_PAYMENT');

UPDATE platform_config billing
JOIN platform_config trade_app ON trade_app.system_code = 'alipay'
  AND trade_app.config_key = 'alipay.trade-app-id'
  AND trade_app.config_value IS NOT NULL
  AND trade_app.config_value <> ''
SET billing.config_value = trade_app.config_value,
    billing.updated_at = NOW()
WHERE billing.system_code = 'alipay'
  AND billing.config_key = 'billing.alipay.op-app-id'
  AND (billing.config_value IS NULL OR billing.config_value = '');

COMMIT;

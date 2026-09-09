-- Add public runtime defaults and repair display text for Alipay rental.
-- Run in the equipment_platform database. Existing deployment values are preserved.
-- Merchant/contact/credential fields are blank; configure them before live transactions.
-- A JWT secret is generated only for a new or empty value; reruns keep an existing secret.

SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO platform_config
  (system_code, config_group, config_key, config_value, value_type, secret_flag, enabled, display_name, remark, sort, created_at, updated_at)
VALUES
  ('common', 'site', 'site.footer.icp-record', '', 'string', 0, 1, 'ICP备案号', '展示在所有后台页面底部', 700, NOW(), NOW()),
  ('common', 'site', 'site.footer.police-record', '', 'string', 0, 1, '公安备案号', '展示在所有后台页面底部，请替换为真实公安备案号', 710, NOW(), NOW()),
  ('common', 'site', 'site.footer.copyright', 'Copyright © honestTai contributors', 'string', 0, 1, '版权信息', '展示在所有后台页面底部', 720, NOW(), NOW()),

  ('common', 'oss', 'oss.storage-type', 'aliyun', 'string', 0, 1, '图片上传方式', 'aliyun=阿里云 OSS；local=本地 uploads 目录', 241, NOW(), NOW()),
  ('common', 'oss', 'oss.local-root-path', '/app/uploads', 'string', 0, 1, '本地上传根目录', '图片上传方式为 local 时写入的服务器目录', 242, NOW(), NOW()),
  ('common', 'oss', 'oss.aliyun-public-base-url', '', 'url', 0, 1, '阿里云图片回显前缀', '图片上传方式为 aliyun 时使用的 OSS/CDN 回显地址', 243, NOW(), NOW()),
  ('common', 'oss', 'oss.local-public-base-url', '/uploads', 'url', 0, 1, '本地图片回显前缀', '图片上传方式为 local 时使用的本地域名回显地址', 244, NOW(), NOW()),
  ('common', 'oss', 'oss.public-base-url', '', 'url', 0, 0, '旧图片回显前缀', '兼容旧版本，优先使用 oss.aliyun-public-base-url 或 oss.local-public-base-url', 245, NOW(), NOW()),

  ('alipay', 'alipay', 'alipay.openapi.gateway', 'https://openapi.alipay.com/gateway.do', 'url', 0, 1, '支付宝网关地址', '支付宝开放平台网关', 310, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.appid', '', 'secret', 1, 1, '支付宝 AppId', '支付宝开放平台应用 ID，需要在客户支付宝开放平台填写', 320, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.private-key', '', 'secret', 1, 1, '支付宝应用私钥', '支付宝租赁服务端签名私钥，需要在客户支付宝开放平台填写', 325, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.public-key', '', 'secret', 1, 1, '支付宝公钥', '支付宝开放平台公钥，需要在客户支付宝开放平台填写', 326, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.mch-uuid', '', 'secret', 1, 1, '支付宝商户 UUID', '旧 my.mch_uuid 配置值', 327, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.secret-key', '', 'secret', 1, 1, '支付宝接口内容加密 AES 密钥', '用于解密 my.getPhoneNumber 返回的手机号密文；填写支付宝开放平台-开发设置里的接口内容加密 AES 密钥，不是 alipay.public-key 或 alipay.private-key', 328, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.notify.trade-url', '', 'url', 0, 1, '支付宝交易通知地址', '支付、预授权、售后通知地址，需要配置为公网回调地址', 330, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.notify.charset', 'GBK', 'string', 0, 1, '支付宝通知字符集', '支付宝异步通知验签默认 charset', 331, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.notify.sign-type', 'RSA2', 'string', 0, 1, '支付宝通知签名类型', '支付宝异步通知验签默认 sign_type', 332, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.certify.return-url', '', 'url', 0, 1, '支付宝实人认证回跳地址', '小程序实人认证完成后的回跳地址', 340, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.zm-service-id', '', 'string', 0, 1, '芝麻信用服务 ID', '支付宝租赁风控咨询服务 ID', 350, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.rent.category-id', '', 'string', 0, 1, '支付宝租赁类目 ID', '租赁相机类目 ID，可在配置中心编辑时选择支付宝租赁类目', 360, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.payee-user-id', '', 'secret', 1, 1, '支付宝收款账号 ID', '资金授权收款账号 userId，需要在客户支付宝账号中填写', 370, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.trade-app-id', '', 'secret', 1, 1, '支付宝交易 AppId', '租赁交易组件 AppId，需要在客户支付宝开放平台填写', 380, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.protocol.path', 'pages/agreement/rental-contract', 'string', 0, 1, '租赁协议路径', '小程序租赁协议页面路径', 390, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.protocol.name', '租赁协议', 'string', 0, 1, '租赁协议名称', '支付宝租赁协议展示名称', 400, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.risk.protocol-url', '', 'string', 0, 1, '风控协议 URL', '下单前风控咨询协议 URL', 401, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.risk.protocol-name', '租赁风控授权协议', 'string', 0, 1, '风控协议名称', '下单前风控咨询协议名称', 402, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.item.fineness', 'secondHand', 'string', 0, 1, '支付宝商品成色', '租赁商品成色：wholeNew 全新，secondHand 二手，默认二手', 403, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.item.fineness-grade', '95new', 'string', 0, 1, '支付宝商品成色等级', '二手商品成色等级：99new、95new、90new、80new、70new，默认 95new', 404, NOW(), NOW()),
  ('alipay', 'alipay', 'alipay.rent-model', 'R00001', 'string', 0, 1, '支付宝租赁模式', '租赁行业商品 rent_model', 405, NOW(), NOW()),
  ('alipay', 'risk', 'alipay.risk.provider', 'rent-shield', 'string', 0, 1, '支付宝风险接口选择', 'rent-shield=租安盾租赁行业风险咨询；cloud-rent-risk=支付宝云智能租赁风控；默认租安盾', 406, NOW(), NOW()),
  ('alipay', 'risk', 'alipay.rent-risk.risk-biz-scene', 'RENT_ORDER', 'string', 0, 1, '租安盾风险业务场景', 'alipay.commerce.rent.risk.consult 的 risk_biz_scene，默认 RENT_ORDER', 407, NOW(), NOW()),
  ('alipay', 'risk', 'alipay.rent-risk.consult-risk-types', '', 'string', 0, 1, '租安盾咨询风险类型', '逗号分隔；留空时按支付宝租安盾接口默认策略返回', 408, NOW(), NOW()),
  ('alipay', 'risk', 'alipay.rent-risk.fee-risk-model', 'true', 'boolean', 0, 1, '租安盾计费风控开关', '商品详情页组件/签约链路中的 fee_risk_model，true 表示该笔订单启用租安盾风控策略', 409, NOW(), NOW()),
  ('alipay', 'risk', 'alipay.cloud-rent-risk.risk-biz-scene', 'RENT_ORDER', 'string', 0, 1, '云风控风险业务场景', 'alipay.cloud.traas.cloudrisk.rentrisk.query 的 risk_biz_scene，默认 RENT_ORDER', 410, NOW(), NOW()),
  ('alipay', 'risk', 'alipay.cloud-rent-risk.customer-type', 'MOBILE', 'string', 0, 1, '云风控客户标识类型', '默认 MOBILE；可按支付宝云风控接口支持值改为 CERT_NO、ALIPAY_USER_ID 或 ALIPAY_OPEN_ID', 411, NOW(), NOW()),
  ('alipay', 'risk', 'alipay.cloud-rent-risk.source', 'ALIPAY', 'string', 0, 1, '云风控订单来源', '支付宝云智能租赁风控 source，默认 ALIPAY', 412, NOW(), NOW()),
  ('alipay', 'risk', 'alipay.cloud-rent-risk.user-authorization', '1', 'string', 0, 1, '云风控用户授权标识', '支付宝云智能租赁风控 user_authorization，默认 1', 413, NOW(), NOW()),

  ('alipay', 'business', 'rent.return.address.detail', '', 'string', 0, 1, '统一归还地址', '支付宝租赁默认归还详细地址', 410, NOW(), NOW()),
  ('alipay', 'business', 'rent.return.consignee', '', 'string', 0, 1, '统一归还联系人', '支付宝租赁默认归还联系人', 420, NOW(), NOW()),
  ('alipay', 'business', 'rent.return.mobile', '', 'string', 0, 1, '统一归还联系电话', '支付宝租赁默认归还联系电话，需要填写真实客服电话或收件电话', 430, NOW(), NOW()),
  ('alipay', 'business', 'rent.return.address.json', '[]', 'json', 0, 1, '统一归还地址 JSON', '完整地址结构，兼容小程序或接口使用', 440, NOW(), NOW()),
  ('alipay', 'business', 'rent.service-phone', '', 'string', 0, 1, '支付宝租赁客服电话', '小程序公开配置和商品同步客服电话，需要填写真实客服电话', 450, NOW(), NOW()),
  ('alipay', 'runtime', 'alipay.demo-mode.enabled', 'false', 'boolean', 0, 1, '支付宝 Demo 阻断模式', 'true 时阻断所有支付宝 SDK 网络请求并返回明确的本地模拟响应；客户交付必须为 false', 451, NOW(), NOW()),

  ('alipay', 'goods-sync', 'alipay.goods.sync.asset-base-url', '', 'url', 0, 1, '支付宝商品同步图片基础地址', '同步商品图片时拼接相对路径使用', 460, NOW(), NOW()),
  ('alipay', 'goods-sync', 'alipay.goods.sync.category-id', '', 'string', 0, 1, '支付宝商品开放类目 ID', '小程序商品提报使用的支付宝开放平台叶子类目 ID，不是 RENT_* 租赁组件类目', 470, NOW(), NOW()),
  ('alipay', 'goods-sync', 'alipay.goods.sync.business-model', '0', 'string', 0, 1, '支付宝商品同步业务模式', '0 日租，1 月租', 480, NOW(), NOW()),
  ('alipay', 'goods-sync', 'alipay.goods.sync.item-details-page-model', '0', 'string', 0, 1, '支付宝商品详情页模式', '0=自定义详情页，需配置已验收的 path；1=官方插件详情页，需支付宝侧验收；留空=不传详情页模式', 490, NOW(), NOW()),
  ('alipay', 'goods-sync', 'alipay.goods.sync.path-template', 'alipays://platformapi/startapp?appId=2021006158696542&page=pages/goods/detail&query=goodId%3D{goodId}', 'string', 0, 1, '支付宝商品详情页路径模板', 'item-details-page-model=0 时使用，{goodId} 会替换为本地商品 ID/outItemId', 500, NOW(), NOW()),

  ('alipay', 'miniapp', 'miniapp.jwt.server-secret', HEX(RANDOM_BYTES(32)), 'secret', 1, 1, '小程序 JWT 服务端密钥', '支付宝小程序登录 JWT 签名密钥；空值时由脚本自动生成', 310, NOW(), NOW()),
  ('alipay', 'miniapp', 'miniapp.jwt.issuer', 'equipment-alipay', 'string', 0, 1, '小程序 JWT 签发方', '支付宝小程序 JWT issuer', 311, NOW(), NOW()),
  ('alipay', 'miniapp', 'miniapp.jwt.audience', 'rent-miniapp', 'string', 0, 1, '小程序 JWT 受众', '支付宝小程序 JWT audience', 312, NOW(), NOW()),
  ('alipay', 'miniapp', 'miniapp.jwt.expire-minutes', '10080', 'number', 0, 1, '小程序 JWT 过期分钟', '默认 7 天', 313, NOW(), NOW()),
  ('alipay', 'miniapp', 'miniapp.order.detail-path-template', '/pages/order-detail/order-detail?orderId={orderId}', 'string', 0, 1, '小程序订单详情路径模板', '订单详情跳转路径，支持 {orderId} 占位符', 314, NOW(), NOW()),

  ('alipay', 'order', 'rent.pay.timeout-express', '30m', 'string', 0, 1, '支付宝支付超时时间', '支付宝 pay_timeout_express，例如 30m', 600, NOW(), NOW()),
  ('alipay', 'order', 'rent.pay.lock-timeout-minutes', '5', 'number', 0, 1, '支付锁超时分钟', '防止重复支付的锁超时时间', 601, NOW(), NOW()),
  ('alipay', 'order', 'rent.order.create-lock-timeout-seconds', '10', 'number', 0, 1, '下单锁超时秒数', '防止重复下单的锁超时时间', 602, NOW(), NOW()),
  ('alipay', 'order', 'rent.order.create-limit-window-minutes', '5', 'number', 0, 1, '下单限流窗口分钟', '限制同一用户短时间频繁下单的统计窗口', 603, NOW(), NOW()),
  ('alipay', 'order', 'rent.order.create-limit-max-times', '3', 'number', 0, 1, '下单限流次数', '下单限流窗口内允许的最大次数', 604, NOW(), NOW()),
  ('alipay', 'order', 'rent.order.unpaid-timeout-minutes', '30', 'number', 0, 1, '未支付订单超时分钟', '超过该时间仍未支付的订单可按未支付超时处理', 605, NOW(), NOW()),
  ('alipay', 'order', 'rent.order.unpaid-max-count', '3', 'number', 0, 1, '未支付订单最大数量', '同一用户允许存在的未支付订单数量', 606, NOW(), NOW()),
  ('alipay', 'order', 'rent.aftersale.scan-range-days', '7', 'number', 0, 1, '售后同步扫描天数', '售后同步默认向前扫描的天数', 607, NOW(), NOW()),

  ('alipay', 'contract', 'rent.contract.title', '租赁协议', 'string', 0, 1, '租赁协议标题', '签约协议页面与 PDF 标题', 130, NOW(), NOW()),
  ('alipay', 'contract', 'rent.contract.subtitle', '', 'string', 0, 1, '租赁协议副标题', '签约协议页面说明文案', 131, NOW(), NOW()),
  ('alipay', 'contract', 'rent.contract.merchant.name', '', 'string', 0, 1, '租赁协议-出租方名称', '签约协议和 PDF 中的出租方公司名称，请按营业执照核对', 132, NOW(), NOW()),
  ('alipay', 'contract', 'rent.contract.merchant.credit-code', '', 'string', 0, 1, '租赁协议-统一社会信用代码', '签约协议和 PDF 中的出租方统一社会信用代码，需要按营业执照填写', 133, NOW(), NOW()),
  ('alipay', 'contract', 'rent.contract.merchant.legal-representative', '', 'string', 0, 1, '租赁协议-法定代表人', '签约协议和 PDF 中的出租方法定代表人，需要按营业执照填写', 134, NOW(), NOW()),
  ('alipay', 'contract', 'rent.contract.merchant.registered-address', '', 'string', 0, 1, '租赁协议-注册地址', '签约协议和 PDF 中的出租方注册地址', 135, NOW(), NOW()),
  ('alipay', 'contract', 'rent.contract.merchant.address', '', 'string', 0, 1, '租赁协议-联系地址', '签约协议和 PDF 中的出租方发货或联系地址', 136, NOW(), NOW()),
  ('alipay', 'contract', 'rent.contract.sections.json', '[]', 'json', 0, 1, '租赁协议条款 JSON', 'JSON 数组，字段为 title/items；支持 ${depositText} 等订单变量', 137, NOW(), NOW()),

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
  ('alipay', 'esign', 'esign.auth-token', '', 'secret', 1, 1, '签署网关授权令牌', '调用签署网关接口使用的 Authorization，需要填写真实授权令牌', 551, NOW(), NOW()),
  ('alipay', 'esign', 'esign.app-id', '', 'secret', 1, 1, '签署网关 AppId', '可选，透传到 X-Tsign-Open-App-Id', 552, NOW(), NOW()),
  ('alipay', 'esign', 'esign.upload-path', '', 'string', 0, 1, '合同上传文件路径', '上传合同 PDF 的接口路径', 553, NOW(), NOW()),
  ('alipay', 'esign', 'esign.sign-flow-path', '', 'string', 0, 1, '创建签署流程路径', '创建签署流程接口路径', 554, NOW(), NOW()),
  ('alipay', 'esign', 'esign.query-flow-path', '', 'string', 0, 1, '查询签署流程路径', '支持 {flowId} 占位符', 555, NOW(), NOW()),
  ('alipay', 'esign', 'esign.download-path', '', 'string', 0, 1, '下载签署文件路径', '支持 {flowId} 占位符', 556, NOW(), NOW()),
  ('alipay', 'esign', 'esign.notify-url', '', 'url', 0, 1, '签署回调通知地址', '创建签署流程时传给签署网关的回调地址', 557, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.app-id', '', 'string', 0, 1, '签署小程序 AppId', '前端跳转签署小程序用', 558, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.env', 'prod', 'string', 0, 1, '签署小程序环境', 'prod/test', 559, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.path', 'pages/startup/index', 'string', 0, 1, '签署小程序启动页', 'e签宝支付宝小程序固定启动页 pages/startup/index', 560, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.page', 'sign', 'string', 0, 1, '签署小程序签署页', 'e签宝支付宝小程序 extraData.query.page，默认 sign', 561, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.forward-home', 'true', 'boolean', 0, 1, '签署完成引导首页', 'e签宝支付宝小程序 forwardHome 参数，默认 true', 562, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.open-type', 'miniProgram', 'string', 0, 1, '签署小程序打开方式', '默认 miniProgram，按 e签宝支付宝小程序官方跳转方式打开', 563, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.plugin-page', 'esign', 'string', 0, 1, '签署插件页', '兼容旧 e签宝插件入口', 564, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.skip-result', 'false', 'boolean', 0, 1, '签署跳过结果页', '兼容旧插件入口，签署完成后是否跳过结果页', 565, NOW(), NOW()),
  ('alipay', 'esign', 'esign.miniapp.skip-guide', 'false', 'boolean', 0, 1, '签署跳过引导页', '兼容旧插件入口，签署前是否跳过引导页', 566, NOW(), NOW()),

  ('alipay', 'security', 'my.jwt.server-secret', '', 'secret', 1, 1, '支付宝小程序 JWT 密钥', '旧版本兼容配置，优先使用 miniapp.jwt.server-secret', 335, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  config_group = VALUES(config_group),
  config_value = CASE
    WHEN VALUES(secret_flag) = 1 AND (platform_config.config_value IS NULL OR platform_config.config_value = '') THEN VALUES(config_value)
    WHEN VALUES(secret_flag) = 1 THEN platform_config.config_value
    WHEN platform_config.config_value IS NULL OR platform_config.config_value = '' THEN VALUES(config_value)
    ELSE platform_config.config_value
  END,
  value_type = VALUES(value_type),
  secret_flag = VALUES(secret_flag),
  enabled = platform_config.enabled,
  display_name = VALUES(display_name),
  remark = VALUES(remark),
  sort = VALUES(sort),
  updated_at = NOW();

-- Do not force-enable withhold entry or scheduled deductions during a migration.
-- Existing nonempty operator choices are preserved by the upsert above.
-- Enable the required capability in the platform console after integration validation.

UPDATE platform_config
SET config_value = 'miniProgram',
    updated_at = NOW()
WHERE system_code = 'alipay'
  AND config_key = 'esign.miniapp.open-type'
  AND (config_value IS NULL OR config_value = '' OR config_value = 'plugin');

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

UPDATE platform_config
SET
  display_name = '小程序 JWT 过期分钟',
  remark = '默认 7 天',
  value_type = 'number',
  secret_flag = 0,
  enabled = 1,
  config_group = 'miniapp',
  updated_at = NOW()
WHERE system_code = 'alipay'
  AND config_key = 'miniapp.jwt.expire-minutes';

UPDATE platform_config
SET
  config_value = '/pages/order-detail/order-detail?orderId={orderId}',
  display_name = '小程序订单详情路径模板',
  remark = '订单详情跳转路径，支持 {orderId} 占位符',
  value_type = 'string',
  secret_flag = 0,
  enabled = 1,
  config_group = 'miniapp',
  updated_at = NOW()
WHERE system_code = 'alipay'
  AND config_key = 'miniapp.order.detail-path-template';

UPDATE platform_config
SET
  display_name = '未支付订单超时分钟',
  remark = '超过该时间仍未支付的订单可按未支付超时处理',
  value_type = 'number',
  secret_flag = 0,
  enabled = 1,
  config_group = 'order',
  updated_at = NOW()
WHERE system_code = 'alipay'
  AND config_key = 'rent.order.unpaid-timeout-minutes';

UPDATE platform_config
SET
  display_name = '未支付订单最大数量',
  remark = '同一用户允许存在的未支付订单数量',
  value_type = 'number',
  secret_flag = 0,
  enabled = 1,
  config_group = 'order',
  updated_at = NOW()
WHERE system_code = 'alipay'
  AND config_key = 'rent.order.unpaid-max-count';

-- Deployment-specific merchant, contact, filing and contract values are intentionally
-- left unchanged. Configure them for your own deployment in the platform console.

UPDATE platform_config
SET display_name = CASE config_key
      WHEN 'esign.auth-mode' THEN 'e签宝鉴权方式'
      WHEN 'esign.app-secret' THEN 'e签宝 AppSecret'
      WHEN 'esign.auto-archive' THEN '签署后自动归档'
      WHEN 'esign.sign-platform' THEN 'e签宝签署平台'
      WHEN 'esign.notice-type' THEN 'e签宝通知方式'
      WHEN 'esign.account.third-party-prefix' THEN 'e签宝账号唯一前缀'
      WHEN 'esign.signer.id-type' THEN '签署人证件类型'
      WHEN 'esign.signer.order' THEN '用户签署顺序'
      WHEN 'esign.signer.seal-type' THEN '用户签署方式'
      WHEN 'esign.signer.sign-type' THEN '用户签署类型'
      WHEN 'esign.signer.assigned-pos' THEN '固定用户签署位置'
      WHEN 'esign.signer.pos-page' THEN '用户签署页码'
      WHEN 'esign.signer.pos-x' THEN '用户签署 X 坐标'
      WHEN 'esign.signer.pos-y' THEN '用户签署 Y 坐标'
      WHEN 'esign.platform-sign.enabled' THEN '企业自动盖章开关'
      WHEN 'esign.seal-id' THEN '企业印章 sealId'
      WHEN 'esign.platform-sign.order' THEN '企业盖章顺序'
      WHEN 'esign.platform-sign.sign-type' THEN '企业盖章签署类型'
      WHEN 'esign.platform-sign.pos-page' THEN '企业盖章页码'
      WHEN 'esign.platform-sign.pos-x' THEN '企业盖章 X 坐标'
      WHEN 'esign.platform-sign.pos-y' THEN '企业盖章 Y 坐标'
      ELSE display_name
    END,
    remark = CASE config_key
      WHEN 'esign.auth-mode' THEN 'oauth2=后端使用 AppID/AppSecret 自动获取 OAuthToken；留空则兼容旧签署网关'
      WHEN 'esign.app-secret' THEN 'e签宝开放平台应用密钥，只允许写入配置中心/数据库，不允许写入代码'
      WHEN 'esign.auto-archive' THEN '开启后所有签署人签署完成自动归档，便于后续下载签署文件'
      WHEN 'esign.sign-platform' THEN '2=支付宝签，用于支付宝小程序唤起 e签宝签署'
      WHEN 'esign.notice-type' THEN '逗号分隔：1短信、2邮件、3支付宝、4钉钉；留空时不额外通知，由小程序跳转签署'
      WHEN 'esign.account.third-party-prefix' THEN '创建个人签署账号时拼接本地 userUuid，避免和其他系统 thirdPartyUserId 冲突'
      WHEN 'esign.signer.id-type' THEN '默认中国大陆居民身份证'
      WHEN 'esign.signer.order' THEN '未启用平台自动盖章时默认为 1；若启用企业自动盖章可改为 2'
      WHEN 'esign.signer.seal-type' THEN '0=手绘签名'
      WHEN 'esign.signer.sign-type' THEN '0=不限签署位置；快速接入时由用户在 e签宝内完成签署确认'
      WHEN 'esign.signer.assigned-pos' THEN 'false=不固定签署坐标；需要固定落章位置时改为 true 并配置 pos-page/pos-x/pos-y'
      WHEN 'esign.signer.pos-page' THEN '固定用户签署位置时使用'
      WHEN 'esign.signer.pos-x' THEN '固定用户签署位置时使用'
      WHEN 'esign.signer.pos-y' THEN '固定用户签署位置时使用'
      WHEN 'esign.platform-sign.enabled' THEN '快速接入默认关闭；正式合同需要企业章时打开并确保 e签宝后台有默认企业章或配置 sealId'
      WHEN 'esign.seal-id' THEN '留空时 e签宝使用默认企业章；指定印章时填 e签宝 sealId'
      WHEN 'esign.platform-sign.order' THEN '启用企业自动盖章时使用'
      WHEN 'esign.platform-sign.sign-type' THEN '1=单页签署'
      WHEN 'esign.platform-sign.pos-page' THEN '启用企业自动盖章时使用'
      WHEN 'esign.platform-sign.pos-x' THEN '启用企业自动盖章时使用'
      WHEN 'esign.platform-sign.pos-y' THEN '启用企业自动盖章时使用'
      ELSE remark
    END,
    updated_at = NOW()
WHERE system_code = 'alipay'
  AND config_key IN (
    'esign.auth-mode',
    'esign.app-secret',
    'esign.auto-archive',
    'esign.sign-platform',
    'esign.notice-type',
    'esign.account.third-party-prefix',
    'esign.signer.id-type',
    'esign.signer.order',
    'esign.signer.seal-type',
    'esign.signer.sign-type',
    'esign.signer.assigned-pos',
    'esign.signer.pos-page',
    'esign.signer.pos-x',
    'esign.signer.pos-y',
    'esign.platform-sign.enabled',
    'esign.seal-id',
    'esign.platform-sign.order',
    'esign.platform-sign.sign-type',
    'esign.platform-sign.pos-page',
    'esign.platform-sign.pos-x',
    'esign.platform-sign.pos-y'
  );

UPDATE platform_config
SET display_name = CASE config_key
      WHEN 'spring.mail.host' THEN 'SMTP Host'
      WHEN 'spring.mail.port' THEN 'SMTP Port'
      WHEN 'spring.mail.username' THEN 'SMTP 用户名'
      WHEN 'spring.mail.password' THEN 'SMTP 密码'
      WHEN 'spring.mail.protocol' THEN 'SMTP 协议'
      WHEN 'spring.mail.smtp.ssl.enable' THEN 'SMTP SSL'
      ELSE display_name
    END,
    remark = CASE config_key
      WHEN 'spring.mail.host' THEN '系统通知邮件 SMTP 服务地址'
      WHEN 'spring.mail.port' THEN '系统通知邮件 SMTP 端口'
      WHEN 'spring.mail.username' THEN '系统通知邮件发件账号'
      WHEN 'spring.mail.password' THEN '系统通知邮件发件密码'
      WHEN 'spring.mail.protocol' THEN '系统通知邮件协议'
      WHEN 'spring.mail.smtp.ssl.enable' THEN '系统通知邮件 SSL 开关'
      ELSE remark
    END,
    updated_at = NOW()
WHERE system_code = 'common'
  AND config_key IN (
    'spring.mail.host',
    'spring.mail.port',
    'spring.mail.username',
    'spring.mail.password',
    'spring.mail.protocol',
    'spring.mail.smtp.ssl.enable'
  );

COMMIT;

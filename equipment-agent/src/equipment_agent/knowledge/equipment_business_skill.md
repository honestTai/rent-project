# 无人机设备管理系统业务 Skill

## 使用边界

- 只做只读分析：允许读取只读数据库、只读接口、日志、本地知识库和公开网页。
- 禁止写入：不调用新增、更新、删除、退款、扣款、发货、关闭、同步重试、状态修复等会改变业务状态的接口或 SQL。
- 内部业务数据优先来自只读数据库和只读接口；所有联网查询统一走联网 MCP，只用于官方文档、开放平台规则、公开资料和背景说明。
- 手机号、地址、身份证、token、cookie、签名、授权号、合同路径、完整订单号、完整请求响应报文默认不展示，也不能放入联网搜索关键词。

## 业务域路由

| 用户问题关键词 | 目标业务系统 | 主要模块 | 典型工具方向 |
| --- | --- | --- | --- |
| 支付宝、租赁小程序、押金、预授权、租盾、分期、归还、售后、商品同步、回调 | alipay | 支付宝租赁 | 租赁订单、支付分期、押金扣减、商品同步、风控、售后、回调日志 |
| 设备租赁、设备、库存、设备分类、租金、订单来源、异常设备、维修 | rental | 设备租赁 | 传统设备租赁订单、设备档案、库存、维修、报表 |
| 二手、回收、卖出、买入、成本、利润、抖音、抖店、同步、分佣 | secondhand | 二手交易 | 二手买入卖出、抖音订单池、同步日志、维修、分佣、分析报表 |
| 登录、用户、角色、菜单、按钮权限、RBAC、配置、通知、任务、系统日志、监控 | platform | 平台中台 | 统一登录、权限、配置、通知、计划任务、日志、服务监控 |

## 支付宝租赁

### 业务范围

- 面向支付宝租赁小程序和租赁后台，覆盖商品、租赁下单、支付、分期、预授权、租盾、本地风控、押金扣减、归还寄回、售后赔付、经营分析和报表。
- 后台接口主要在 `/api/web/**`，小程序接口主要在 `/api/rent/v1/**`，支付宝通知在 `/notify/**`。
- 金额字段大多按分存储，展示元必须 `/ 100`，不要把分当元。

### 表与字段

| 表 | 对应业务 | 关键字段说明 |
| --- | --- | --- |
| `rent_order` | 支付宝租赁订单主表 | `order_id`/`order_no` 是订单标识；`user_id` 是用户；`goods_id`/`goods_title` 是商品；`created_at` 是毫秒时间戳；`paid_amount`、`total_amount`、`deposit`、`remaining_deposit` 是分；`status` 是本地订单状态；`alipay_status` 是支付宝履约状态；`payment_time`、`finish_time` 可用于状态流转分析。 |
| `installment_plan` | 订单分期计划 | `order_id` 关联 `rent_order.order_id`；`period_no` 是期数；`period_amount` 是分；`plan_pay_time` 是计划支付时间；`pay_time` 是实际支付时间；`status=1` 可作为已支付分期口径。 |
| `order_operation_log` | 订单操作与回调台账 | `order_id`/`order_no` 定位订单；`operation_type`、`operation_desc`、`status_before`、`status_after`、`fail_reason` 用于排查回调、人工操作和状态流转。 |
| `goods` | 租赁商品主表 | `goods_id`、`title`、`category_id`、`status`、`alipay_item_id` 用于商品列表、上下架和支付宝商品映射。 |
| `goods_sku` | 租赁商品 SKU | `goods_id` 关联商品；`sku_id`、`price`、`stock`、`alipay_sku_id` 用于 SKU 价格、库存和支付宝同步。 |
| `alipay_goods_sync_log` | 支付宝商品同步日志 | `goods_id`、`request_id`、`sync_type`、`sync_status`、`fail_reason` 用于商品同步失败归因。 |
| `rent_deposit_deduct_record` | 押金扣减与售后扣款 | `order_id`/`order_no` 定位订单；`fee_type`、`reason_code`、`deduct_amount` 是分；`status`、`aftersale_status`、`fail_reason` 用于扣款和赔付进度。 |
| `rent_order_return_record` | 归还寄回记录 | `order_id`/`order_no` 定位订单；`return_type`、`express_no`、`status`、`submitted_at`、`fail_reason` 用于归还链路。 |
| `rent_aftersale_sync_snapshot` | 支付宝售后同步快照 | `aftersale_no`、`out_aftersale_id`、`aftersale_type`、`aftersale_status`、`deduct_amount`、`match_status`、`import_status`、`payload_json`、`fail_reason` 用于售后同步和扣款匹配。 |
| `periodic_report` | 支付宝租赁周期报表 | `report_date`、`metric_type`、`metric_value` 等用于报表中心；实际字段以只读 schema 为准。 |
| `rent_ios_device_token` | iOS 管理端推送设备 | `user_name`、`user_uuid`、`device_token`、`environment`、`bundle_id`、`active`、`last_seen_at` 用于审核推送设备管理，敏感字段不明文展示。 |
| `rent_sys_config` | 支付宝租赁配置 | 配置键值、启用状态和更新时间用于排查功能开关，不能通过 agent 修改。 |
| `user` | 支付宝租赁用户 | 用户标识、手机号、实名、芝麻等字段可能敏感；默认只做聚合或脱敏，不输出明细。 |

### 查询口径

- 订单数默认 `COUNT(1)` from `rent_order`。
- 支付宝租赁销售额或到账收益默认 `SUM(COALESCE(paid_amount, 0)) / 100`。
- 活跃用户数默认按订单行为统计：从 `rent_order` 按 `created_at` 时间范围过滤，`COUNT(DISTINCT user_id)`；按天趋势时按 `DATE(FROM_UNIXTIME(created_at / 1000))` 分组。
- 新增用户数才使用 `user.created_at`，只能做聚合统计，不查询或展示手机号、身份证、地址、openid、uuid 等敏感字段。
- 按自然日或自然月查 `rent_order.created_at` 时，把日期边界换成毫秒 `startMs`/`endMs`，条件使用 `created_at >= :startMs AND created_at < :endMs`。
- 本地订单状态和支付宝履约状态不是同一口径，回答时说明使用 `status` 还是 `alipay_status`。

## 设备租赁

### 业务范围

- 面向传统设备租赁后台，覆盖设备档案、设备分类、库存、租赁订单、订单来源、维修、首页统计和报表中心。
- 这部分接口通常走剩余 `/api/**` 和 `/images/**` 网关路由。

### 表与字段

| 表 | 对应业务 | 关键字段说明 |
| --- | --- | --- |
| `order` | 设备租赁订单主表 | 这是 MySQL 关键字，SQL 必须写成 `` `order` ``；`id` 是订单；`goodsId` 关联设备；`deviceName` 是设备名称；`deviceType` 是设备类别；`sendTime` 是寄出或订单开始口径；`harvestTime`/`backTime` 是收货或结束口径；`runt` 是租金收入。 |
| `device` | 设备档案 | `id`、`deviceName`、`deviceType`、`status`、`classId` 用于设备表现、设备类型和可用状态分析。 |
| `device_class` | 设备分类 | `id`、`name`、`status` 用于分类筛选和设备分组。 |
| `inventory` | 库存记录 | 设备、状态、位置相关字段用于库存状态、可租可用和异常设备分析，实际字段以 schema 为准。 |
| `repair` | 设备维修记录 | 设备编号、维修原因、状态、时间字段用于维修统计和异常设备定位。 |
| `order_source` | 订单来源 | 来源名称、渠道状态和创建时间用于订单渠道统计。 |
| `periodic_report` | 设备租赁报表 | 报表日期、指标和内容用于报表中心，注意与支付宝租赁报表分库分系统。 |

### 查询口径

- 设备租赁订单数默认 `COUNT(1)` from `` `order` ``。
- 设备租赁收入默认 `SUM(runt)`，除非用户明确指定押金、成本或其他口径。
- “哪个设备最好”必须先定义指标：收入、订单数、租期、利润或综合评分，不要直接给主观结论。

## 二手交易与抖音

### 业务范围

- 面向二手设备买入、卖出、维修、分析、周期报表、卖家分佣，以及抖店/抖音订单同步。
- 二手业务接口主要在 `/api/second/**`，维修接口在 `/api/secondRepair/**`。

### 表与字段

| 表 | 对应业务 | 关键字段说明 |
| --- | --- | --- |
| `second_device_out` | 二手卖出订单 | `id` 是卖出记录；`into_id` 关联入库；`buyDate` 是成交日期；`buyMoney` 是销售额；`costPrice` 是成本；`status` 是卖出状态；`can_id` 可关联渠道或分类。 |
| `second_device_into` | 二手买入/入库设备 | `id` 是入库设备；`devName` 是机型或设备名称；`money` 是入库成本；`status` 是库存或处理状态；入库时间字段用于库存周期分析。 |
| `second_douyin_order` | 抖音订单池 | `shop_id`、`order_id`、`sku_order_id`、`product_id`、`sku_id`、`outer_sku_code`、`product_title`、`sku_title`、`platform_order_status`、`pay_amount`、`paid_at`、`sync_status`、`local_into_id`、`local_out_id`、`local_device_code`、`failure_reason` 用于抖音销售和同步失败归因。 |
| `second_douyin_api_log` | 抖音 API 调用日志 | 接口、请求标识、响应状态、错误码、错误信息和时间字段用于开放平台调用排查；敏感请求响应不明文展示。 |
| `second_douyin_message_log` | 抖音消息日志 | 消息类型、订单号、处理状态、失败原因和时间字段用于消息驱动同步排查。 |
| `second_repair` | 二手维修记录 | 设备、维修原因、维修状态、费用、时间字段用于维修统计。 |
| `seller_commission` | 卖家分佣 | 卖家、订单、佣金金额、结算状态和时间字段用于分佣统计。 |
| `secondhand_periodic_report` | 二手交易周期报表 | 报表日期、指标、内容和生成状态用于二手报表中心。 |

### 查询口径

- 二手销售额默认来自 `second_device_out.buyMoney`。
- 二手利润默认 `buyMoney - costPrice`，如果成本字段为空，要说明口径风险。
- 抖音销售额默认来自 `second_douyin_order.pay_amount`，字段单位以只读 schema 和代码为准；不确定时必须说明单位风险。
- 同步失败优先看 `sync_status`、`failure_reason`、API 日志和消息日志，不要只看订单池状态。

## 平台中台

### 业务范围

- 覆盖统一登录、用户、角色、菜单、按钮权限、平台配置、通知渠道、计划任务、系统日志和服务监控。
- 平台接口主要在 `/api/platform/**` 和 `/api/user/**`。

### 表与字段

| 表 | 对应业务 | 关键字段说明 |
| --- | --- | --- |
| `platform_role` | 角色权限 | 角色 ID、角色名称、状态、创建更新时间用于 RBAC 分析。 |
| `platform_user` 或 `user` | 平台用户 | 用户名、手机号、角色、状态等字段涉及敏感信息，默认脱敏或聚合展示。 |
| `platform_menu` | 菜单权限 | 菜单 ID、父级、路径、组件、排序、状态用于菜单和路由权限排查。 |
| `platform_role_menu` | 角色菜单关系 | `role_id`、`menu_id` 用于判断角色是否拥有菜单权限。 |
| `platform_config` | 平台配置 | 配置键、配置值、作用域、启用状态和更新时间用于配置排查，不允许写入。 |
| `platform_notify_channel` | 通知渠道 | 渠道类型、启用状态、接收人和更新时间用于通知排查。 |
| `platform_schedule_task` | 计划任务 | 任务名、cron、启用状态、最近执行结果和时间用于调度排查。 |
| `system_log` | 系统日志 | 操作人、模块、接口、状态、耗时、错误信息和时间字段用于审计和错误定位。 |

## 回答要求

- 优先给结论，再给证据和判断理由；不要展示内部编排、工具参数或工作台信息。
- 有真实 rows 或接口结果时，必须使用真实数值，不得改写或编造。
- 缺少数据库连接、缺少 schema、缺少工具结果时，要说清楚不能给真实数据结论。
- 联网 MCP 结果只能作为公开资料参考，必须由 LLM 汇总并保留来源，不能替代内部订单、收益、用户、押金、库存等真实业务数据。

## Agent 编排规则

### 自动图表

- 用户问“趋势、统计、分析、销售情况、订单量、金额、利润、排行、分布、占比”时，优先返回结构化 `charts`，不要只写文字。
- 时间序列数据使用折线图：横轴通常是 `date`、`day`、`month`、`paid_at` 等时间字段，数值序列用订单数、金额、利润等。
- 排行数据使用柱状图：横轴通常是设备名、商品名、渠道、状态或错误类型，数值序列用订单数、金额、利润或失败次数。
- 金额分布、状态分布、渠道占比使用饼图或分组柱状图；支付宝租赁金额字段按分存储，图表展示前必须转元。
- 如果返回多天或多分类 rows，要给图表摘要和关键指标卡，例如订单数总计、已支付金额总计、利润总计。
- 图表标题必须是业务含义，不要使用 SQL 表达式或字段全集作为标题。

### 日志读取

- 用户提到“报错、异常、失败、超时、回调失败、支付失败、扣款失败、同步失败、日志”时，除了业务只读数据，也要读取容器 `docker logs` 的摘要。
- 支付宝租赁日志优先看 alipay/pay/notify 相关容器；网关路由、鉴权、超时看 gateway 容器；二手抖音同步看 secondhand/sync/douyin 相关容器。
- 日志只用于归因，不展示 `docker logs` 命令、原始日志行、token、手机号、签名、cookie、请求响应明细。
- 日志结论只写异常类型、影响范围、最近命中情况和下一步确认项。

### 联网 MCP

- 所有需要联网的公开信息查询必须走统一联网 MCP，不允许 Agent 直连搜索引擎或网页作为兜底。
- 联网 MCP 只查公开网页、官方文档、开放平台规则、接口说明和背景资料。
- 内部订单数、销售额、用户、押金、扣款、库存、同步失败数量不能通过联网 MCP 查询，必须走只读数据库、只读接口或日志。
- MCP 原始结果由 LLM 结合标题、摘要、来源和链接自行汇总；总结后提出一个与结论直接相关的反追问。

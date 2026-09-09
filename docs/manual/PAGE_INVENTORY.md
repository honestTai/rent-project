# 页面与截图清单

本清单依据当前前端路由与组件源码编写。53 张截图已于 2026-09-09 在本地隔离演示库环境中通过同一个 Kimi WebBridge 会话实际采集，覆盖 33 个页面与状态入口、关键弹窗及三类接入配置；不复用旧客户手册，不展示真实个人资料、凭证或二维码。

页面采用 Hash 路由。下表路由前需拼接对应中台/租赁后台入口及 `#`，例如 `http://127.0.0.1:8084/#/platform/config`。每个实际页面至少一张截图；登录与无权限状态也纳入覆盖。按钮受 RBAC、数据状态和显示设置影响，完整条件见各分册。

| 页面 | 路由（Hash部分） | 截图文件 | 主要按钮/交互 |
| --- | --- | --- | --- |
| 中台登录 | `/login` | `platform-login.png` | 账号、密码、记住账号、登录 |
| 系统总览 | `/platform/overview` | `platform-overview.png` | 周/月/年/日期/刷新；切换系统卡片、进入系统 |
| 配置中心 | `/platform/config` | `platform-config.png` | 系统/分组/关键词自动筛选；新增、重置、编辑、删除 |
| 通知通道 | `/platform/notify` | `platform-notify.png` | 系统/关键词筛选；新增、重置、编辑、删除 |
| 任务中心 | `/platform/task` | `platform-task.png` | 系统/关键词筛选；新增、重置、编辑、删除 |
| 中台监控 | `/platform/monitor` | `platform-monitor.png` | 服务选择、实时刷新、刷新 |
| 中台日志 | `/platform/logs` | `platform-logs.png` | 系统/级别/文件/行数/关键词；刷新、重置 |
| 角色授权 | `/platform/rbac/roles` | `platform-roles.png` | 角色搜索、新增角色、重置、编辑、删除；树勾选、刷新、清空、保存授权 |
| 用户角色 | `/platform/rbac/user-roles` | `platform-user-roles.png` | 角色搜索/选择；账号/姓名/最后登录地点；重置、选择用户、保存角色 |
| 权限资源 | `/platform/rbac/resources` | `platform-resources.png` | 系统权限树搜索、全部；详情搜索、重置 |
| 后台用户 | `/platform/user` | `platform-users.png` | 账号/姓名/手机号/性别；新增用户、重置、编辑、授权、重置密码、删除 |
| 个人中心 | `/platform/profile` | `platform-profile.png` | 修改密码、确认、取消 |
| 租赁登录 | `/login` | `rent-login.png` | 自动跳转中台统一登录（无独立表单） |
| 功能导览 | `/alipay/delivery-showcase` | `rent-overview.png` | 查看操作文档 |
| 经营看板 | `/alipay/dashboard` | `rent-dashboard.png` | 周/月/年/日期/刷新；订单/收入、热门设备排序、显示设置、专题跳转 |
| 用户画像分析 | `/alipay/analysis/user-portrait` | `rent-analysis-user.png` | 周/月/年/日期、刷新数据、显示设置、明细分页 |
| 地域分析 | `/alipay/analysis/region` | `rent-analysis-region.png` | 周/月/年/日期、刷新数据、显示设置、明细分页 |
| 订单分析 | `/alipay/analysis/order` | `rent-analysis-order.png` | 周/月/年/日期、刷新数据、显示设置、明细分页 |
| 设备分析 | `/alipay/analysis/device` | `rent-analysis-device.png` | 周/月/年/日期、刷新数据、显示设置、明细分页 |
| 营收分析 | `/alipay/analysis/revenue` | `rent-analysis-revenue.png` | 周/月/年/日期、刷新数据、显示设置、明细分页 |
| 租期与履约分析 | `/alipay/analysis/rental` | `rent-analysis-rental.png` | 周/月/年/日期、刷新数据、显示设置、明细分页 |
| 报表中心 | `/alipay/reports` | `rent-reports.png` | 报表类型/生成方式/时间；查询、重置、补生成、详情、导出、显示设置 |
| 订单列表 | `/alipay/orders` | `rent-orders.png` | 搜索类型/关键词/状态/时间；查询、重置、账单下载、详情、显示设置；状态操作见订单章节 |
| 售后同步 | `/alipay/aftersales` | `rent-aftersales.png` | 订单号/订单状态/售后状态/日期；查询/扫描、重置、批量导入/导入台账、订单/台账跳转、补完结、显示设置 |
| 租赁商品 | `/alipay/goods` | `rent-goods.png` | 名称/状态；查询、重置、添加商品、批量删除、刷新、修改、SKU、上架/下架/置顶/同步、日志、显示设置 |
| 轮播图设置 | `/alipay/banners` | `rent-banners.png` | 标题/状态；查询、重置、新增、刷新、编辑、启用/停用、删除；上传图片、保存 |
| 小程序目录 | `/alipay/catalog` | `rent-catalog.png` | 目录选择、刷新、新增/编辑/启停/删除；商品选择、清空选择、添加/移除商品 |
| 小程序用户 | `/alipay/users` | `rent-users.png` | 支付宝PID/OpenID、姓名、身份证号；查询、重置、封禁/解封、显示设置 |
| 台账管理 | `/alipay/ledger` | `rent-ledger.png` | 订单号/类型/结果；查询、重置、订单跳转、详情、显示设置 |
| 租赁监控 | `/alipay/monitor` | `rent-monitor.png` | 实时刷新、刷新 |
| 租赁系统日志 | `/alipay/system-logs` | `rent-system-logs.png` | 级别/文件/行数/关键词；查询日志、重置 |
| AI助手 | `/alipay/agent` | `rent-agent.png` | 新会话、历史会话、输入发送、停止、工具/图表/来源查看 |
| 无权限提示 | `/alipay/no-permission` | `rent-no-permission.png` | 静态无权限提示，无页面内按钮 |

截图目录：`docs/assets/screenshots/`。正文从本目录引用 `../assets/screenshots/<slug>.png`。

## 关键弹窗补充截图

仅打开并查看本地演示表单，不提交真实外部业务操作。

| 弹窗/视图 | 建议截图文件 | 打开方式 |
| --- | --- | --- |
| 配置项表单 | `platform-config-edit.png` | 配置中心编辑无敏感值的演示配置 |
| 新增通知通道 | `platform-notify-edit.png` | 通知通道 → 新增 |
| 任务表单 | `platform-task-edit.png` | 任务中心 → 新增 |
| 角色表单 | `platform-role-edit.png` | 角色授权 → 新增角色 |
| 用户表单 | `platform-user-edit.png` | 用户管理 → 新增用户 |
| 权限接口列 | `platform-resources-api.png` | 权限资源 → 右侧表格向右滚动 |
| 修改密码 | `platform-password.png` | 个人中心 → 修改密码（输入保持空白） |
| 商品基础信息 | `rent-goods-edit.png` | 租赁商品 → 演示商品编辑 |
| SKU 编辑 | `rent-goods-sku.png` | 商品编辑 → SKU → 修改/添加 SKU |
| 订单详情 | `rent-order-detail.png` | 演示订单 → 详情 |
| 订单显示设置 | `rent-order-display.png` | 订单列表 → 显示设置 |
| 押金扣减表单 | `rent-order-deduct.png` | 仅打开具备演示状态的订单扣除押金表单，不提交 |
| 合同模块 | `rent-order-contract.png` | 订单详情分段导航 → 协议 |
| 分期账单与代扣状态 | `rent-order-bills.png` | 订单详情分段导航 → 账单/代扣，仅查看虚构账单 |
| 轮播图编辑 | `rent-banner-edit.png` | 轮播图设置 → 新增 |
| 分类编辑 | `rent-catalog-edit.png` | 小程序目录 → 新增分类 |
| 报表补生成 | `rent-report-generate.png` | 报表中心 → 补生成，不提交 |

若演示数据不满足某个弹窗的权限或状态门槛，先补充纯本地演示记录；不得修改页面去伪造真实支付宝/e签宝的成功状态。实际支付、扣款、代扣签约、退款、合同签署、支付宝商品同步均不作为截图演示动作。

## 集成配置补充截图

| 视图 | 截图文件 | 筛选方式 |
| --- | --- | --- |
| 支付宝配置 | `platform-config-alipay.png` | 系统 alipay，分组 alipay 或关键词 alipay. |
| e签宝配置 | `platform-config-esign.png` | 系统 alipay，分组 esign 或关键词 esign. |
| 自动代扣配置 | `platform-config-withhold.png` | 系统 alipay，分组 withhold 或关键词 withhold. |

这些视图属于配置中心同一路由，用于集成分册。凭证保持空白，学习环境的签约入口、代扣任务和电子合同开关关闭。

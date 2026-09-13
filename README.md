<div align="center">

# Rent Project · 租赁业务管理系统

**让商品、租赁订单、合同与履约，在一套系统里协同。**  
**Bring rental products, orders, contracts, and fulfillment together.**

[图文导览 / Visual tour](docs/SHOWCASE.md) · [部署指南 / Deploy](docs/DEPLOYMENT.md) · [下载部署包 / Download](https://github.com/honestTai/rent-project/releases/latest) · [HRouter](https://hrouter.net/home)

</div>

围绕租赁业务组织商品、订单、分期账单、电子合同与经营分析，用统一中台管理权限和配置。

Organize products, orders, installment bills, electronic contracts, and business analytics with shared access and configuration management.

**适合谁 / Who it’s for**  
需要租赁运营后台、支付宝租赁接入和电子签约工作流的团队与开发者。  
Teams and developers building rental operations with Alipay workflows and electronic contracts.

包含服务端、两个 Web 应用与初始化工具，不包含支付宝小程序前端。  
Includes server code, two web apps, and initialization tools; the Alipay Mini Program frontend is not included.

## 从这里开始 · Start here

| 你的目标 / Your goal | 推荐入口 / Start here |
| --- | --- |
| 先看系统能做什么 / Explore the product | [图文导览：商品、订单、合同与经营看板 / Visual tour](docs/SHOWCASE.md) |
| 了解操作与业务边界 / Understand workflows | [逐页操作手册 / User guide](docs/USER_GUIDE.md) |
| 自行部署试用 / Deploy it yourself | [部署说明 / Deployment](docs/DEPLOYMENT.md) · [正式版本 / Releases](https://github.com/honestTai/rent-project/releases/latest) |
| 调整源码与二次开发 / Develop & customize | [快速开始 / Quick start](docs/QUICKSTART.md) · [架构 / Architecture](docs/ARCHITECTURE.md) |

## 系统特色

- **支付宝租赁接入**：租赁交易组件、芝麻信用免押、商品同步、订单与售后通知处理，把平台交易状态与本地运营连接起来。
- **e签宝电子合同**：提供协议 PDF 生成、OAuth2 接入、用户签署流程、签署文件查询与支付宝合同回传；按订单要求检查签署完成后确认收货。
- **分期与代扣能力**：后端提供分期账单、支付宝协议签约、回调与到期扣款任务。实际代扣需要商户能力、有效订单、用户授权及对应配置；当前后台主动签约入口关闭，演示环境也关闭签约与扣款任务。
- **履约与售后**：从发货、收货、归还到买断、押金扣减和赔付，运营人员可查看业务状态及处理记录。
- **统一中台**：用户、角色、菜单、按钮、API 权限、配置、通知与任务集中管理，多套部署分别维护各自参数。
- **经营分析与 AI**：六类专题分析、经营看板、周期报表，以及可选的只读 AI 工作台，辅助查询订单和业务数据。

仓库提供服务端源码、两个 Web 应用、数据库结构与初始化工具。**不包含支付宝小程序前端**；支付宝、电子签署、存储及模型服务需使用部署者自己的应用和凭据。

## 界面预览

以下为本地隔离环境中的虚构演示数据，截图不代表真实交易、签约或经营业绩。完整手册覆盖 33 个页面，并解释各页按钮、筛选、弹窗和操作条件。

**经营看板：按周期查看订单、收入、设备与履约。**

![经营看板演示](docs/assets/screenshots/rent-dashboard.png)

**订单运营：查询履约与押金，核对协议、分期账单和售后。**

![订单列表演示](docs/assets/screenshots/rent-orders.png)

**商品管理：维护商品、SKU、图片和支付宝同步记录。**

![商品管理演示](docs/assets/screenshots/rent-goods.png)

**e签宝配置：统一管理签署模式、回调和应用参数。**

![e签宝配置演示（敏感值为空）](docs/assets/screenshots/platform-config-esign.png)

[逐页图文操作手册](docs/USER_GUIDE.md) · [支付宝 / e签宝 / 代扣接入说明](docs/manual/INTEGRATIONS.md)

## 能做什么

| 模块 | 功能 |
| --- | --- |
| 配置中台 | 用户、角色、菜单与按钮权限、API 权限、配置中心、通知通道、任务配置、日志与监控 |
| 商品运营 | 商品、规格、目录分类、Banner、支付宝商品同步与状态查询 |
| 租赁订单 | 订单查询、分期账单、合同信息、发货、收货、归还、买断等业务流程 |
| 合同与代扣 | e签宝签署、合同查询与回传、分期账单、代扣授权和任务处理 |
| 售后 | 售后扫描、导入、同步、押金扣减与赔付处理 |
| 经营分析 | 用户、地域、订单、设备、营收、租期履约六类分析，周报/月报/年报与导出 |
| AI 助手 | 配置模型后提供只读业务查询、分析与报告；单独部署 Agent 服务 |

各功能的前置条件和操作入口见[操作手册](docs/USER_GUIDE.md)。外部平台接口是否可用取决于应用授权、类目资质和服务配置。

## 快速开始

部署者可以从 [GitHub Releases](https://github.com/honestTai/rent-project/releases) 下载 Docker 发行包和同版 `SHA256SUMS`，校验后按[安装、升级与回滚手册](docs/DEPLOYMENT.md)执行。发行包包含后端 JAR 与两个前端，无需在服务器编译；默认使用独立 MySQL 8 / Redis，仅开放本机入口。安装机需要 Linux、Python 3.10+、Docker Engine 和 Compose 插件 2.20+，首次仍需联网获取 Docker 镜像。密钥保管与问题反馈见[安全说明](SECURITY.md)。

以下流程用于从源码开发和运行：

准备 JDK 8 或兼容 JDK、Maven 3.6+、Node.js 22、MySQL 8 和 Redis。Python 3.10+ 用于初始化工具及可选 Agent。

```bash
git clone https://github.com/honestTai/rent-project.git
cd rent-project
python -m pip install -r customer-deploy/scripts/requirements-init.txt
```

按[初始化文档](docs/INITIALIZATION.md)设置本部署的独立密钥，检查并创建新的空数据库。管理员密码由你首次设置，无共享默认密码。可选 `--demo` 仅用于本机隔离环境，包含虚构下架商品、受限用户、3 种状态订单、分期账单、合同及零金额售后，均以 DEMO 标识；没有真实支付、外部交易身份或扣款授权，全部定时任务默认停用。

```bash
mvn -B -ntp test package
cd equipment_management_system_fornt
npm ci
```

接下来按[快速开始](docs/QUICKSTART.md)设置数据库与 Redis 参数，依次运行注册中心、中台、租赁服务、网关和两个 Web 应用。默认本地入口为中台 `http://localhost:8084`、租赁运营 `http://localhost:8083`。

发行包也支持外部 MySQL / RDS，安装、备份、升级与应用回滚见[部署说明](docs/DEPLOYMENT.md)。默认仅绑定本机入口，可接自己的 HTTPS 反向代理。

## 文档

- [图文导览](docs/SHOWCASE.md)：先用现有演示截图了解主要业务页面。
- [快速开始](docs/QUICKSTART.md)：运行环境、配置、启动顺序与检查。
- [初始化数据](docs/INITIALIZATION.md)：新库预检、管理员、权限、配置模板与虚构数据。
- [操作手册](docs/USER_GUIDE.md)：33 页图文说明，包含筛选、每页按钮、弹窗字段、状态条件和常见问题。
- [支付宝 / e签宝 / 自动代扣](docs/manual/INTEGRATIONS.md)：配置截图、接入顺序、用户授权与当前界面边界。
- [架构说明](docs/ARCHITECTURE.md)：模块分工、端口和 API 链路。
- [部署说明](docs/DEPLOYMENT.md)：打包、Docker Compose、更新、备份与恢复。
- [公开内容复查记录](docs/SECURITY_REVIEW.md)：已检查的公开历史、截图与 Actions 范围、当前结果及检查限制；不等同于应用安全审计。
- [官方资料](docs/REFERENCES.md)：支付宝、许可证和文档工具的官方入口。
- [贡献指南](CONTRIBUTING.md)与[安全说明](SECURITY.md)。

## 开源协议

项目自有源码采用 [GNU AGPL v3 only](LICENSE)，允许依协议使用、修改、部署和商业使用。
修改版通过网络提供服务时，应按第 13 条向与之交互的用户提供对应源码获取方式；分发时需履行适用条款。完整条款见许可证，第三方组件保留原许可，见 [NOTICE](NOTICE) 和 [第三方声明](THIRD_PARTY_NOTICES.md)。

前端页脚提供源代码入口；修改后部署时请将 `VITE_SOURCE_URL` 指向你运行版本的对应源码。
服务器、存储和第三方 API 费用由使用者承担。

## 作者与服务 · Author & services

由 [honestTai](https://github.com/honestTai) 维护。欢迎在 Issues 反馈使用体验；部署协助、培训和定制开发可通过 [邮件](mailto:honest.tai@outlook.com) 联系。  
Maintained by honestTai. Share feedback in Issues, or email me about deployment, training, and customization.

我也运营 [HRouter](https://hrouter.net/home)，提供面向 AI 编程与应用开发的模型接入服务。租赁系统的部署与代码使用不以购买该服务为前提。  
I also operate HRouter, a model-access service for AI coding and applications. It is not a purchase requirement for this rental system.

[了解 HRouter / Explore HRouter](https://hrouter.net/home) · [更多精选项目 / More projects](https://github.com/honestTai)

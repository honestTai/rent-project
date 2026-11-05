<div align="center">

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/hero.zh-CN.svg">
  <img src="docs/assets/hero.zh-CN-light.svg" alt="Rent Project">
</picture>

**让商品、租赁订单、合同与履约，在一套系统里协同。**

围绕租赁业务组织商品、订单、分期账单、电子合同与经营分析，用统一中台管理权限和配置。需要租赁运营后台、支付宝租赁接入和电子签约工作流的团队与开发者。

仓库提供服务端源码、两个 Web 应用、数据库结构与初始化工具。**不包含支付宝小程序前端**；支付宝、电子签署、存储及模型服务需使用部署者自己的应用和凭据。

[图文导览 / Visual tour](docs/SHOWCASE.md) · [逐页操作手册 / User guide](docs/USER_GUIDE.md) · [部署指南 / Deploy](docs/DEPLOYMENT.md) · [下载部署包 / Download](https://github.com/honestTai/rent-project/releases/latest)

[HRouter](https://hrouter.net/home) · [All public projects](https://github.com/honestTai) · [Star & Fork trends](#project-activity)

[English](README.md) · **简体中文**

</div>

## ❤️ 赞助商

| 赞助商 | 简介 |
| :--- | :--- |
| [![HRouter](docs/assets/hrouter-sponsor.svg)](https://hrouter.net/home) | **[HRouter](https://hrouter.net/home)**：面向 AI 编程与应用开发的模型接入、API Key 管理与用量统计，也由作者运营。 |

## 🚀 快速开始

### 发行包部署

从 [GitHub Releases](https://github.com/honestTai/rent-project/releases) 下载 Docker 发行包和同版 `SHA256SUMS`，校验后按[安装、升级与回滚手册](docs/DEPLOYMENT.md)执行。发行包包含后端 JAR 与两个前端，无需在服务器编译；默认使用独立 MySQL 8 / Redis，仅开放本机入口。安装机需要 Linux、Python 3.10+、Docker Engine 和 Compose 插件 2.20+，首次仍需联网获取 Docker 镜像。密钥保管与问题反馈见[安全说明](SECURITY.md)。发行包也支持外部 MySQL / RDS。默认仅绑定本机入口，可接自己的 HTTPS 反向代理。

### 源码开发

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

## ✨ 功能

| 模块 | 功能 |
| --- | --- |
| 配置中台 | 用户、角色、菜单与按钮权限、API 权限、配置中心、通知通道、任务配置、日志与监控 |
| 商品运营 | 商品、规格、目录分类、Banner、支付宝商品同步与状态查询 |
| 租赁订单 | 订单查询、分期账单、合同信息、发货、收货、归还、买断等业务流程 |
| 合同与代扣 | e签宝签署、合同查询与回传、分期账单、代扣授权和任务处理 |
| 售后 | 售后扫描、导入、同步、押金扣减与赔付处理 |
| 经营分析 | 用户、地域、订单、设备、营收、租期履约六类分析，周报/月报/年报与导出 |
| AI 助手 | 配置模型后提供只读业务查询、分析与报告；单独部署 Agent 服务 |

实际代扣需要商户能力、有效订单、用户授权及对应配置；当前后台主动签约入口关闭，演示环境也关闭签约与扣款任务。各功能的前置条件和操作入口见[操作手册](docs/USER_GUIDE.md)。外部平台接口是否可用取决于应用授权、类目资质和服务配置。

## 📚 文档

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

## 📄 开源协议

项目自有源码采用 [GNU AGPL v3 only](LICENSE)，允许依协议使用、修改、部署和商业使用。
修改版通过网络提供服务时，应按第 13 条向与之交互的用户提供对应源码获取方式；分发时需履行适用条款。完整条款见许可证，第三方组件保留原许可，见 [NOTICE](NOTICE) 和 [第三方声明](THIRD_PARTY_NOTICES.md)。

前端页脚提供源代码入口；修改后部署时请将 `VITE_SOURCE_URL` 指向你运行版本的对应源码。
服务器、存储和第三方 API 费用由使用者承担。

由 [honestTai](https://github.com/honestTai) 维护。欢迎在 Issues 反馈使用体验；部署协助、培训和定制开发可通过 [邮件](mailto:honest.tai@outlook.com) 联系。我也运营 [HRouter](https://hrouter.net/home)，提供面向 AI 编程与应用开发的模型接入服务。租赁系统的部署与代码使用不以购买该服务为前提。

<a id="project-activity"></a>

## 📈 Star 历史

当前 Star / Fork 数量与留存事件历史，计划每日更新。

<a href="https://www.star-history.com/#honestTai/rent-project&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=honestTai/rent-project&type=Date&theme=dark">
    <img src="https://api.star-history.com/svg?repos=honestTai/rent-project&type=Date" alt="rent-project 的 Star 历史">
  </picture>
</a>

[每日实测趋势](https://raw.githubusercontent.com/honestTai/honestTai/main/assets/metrics/rent-project-daily.svg) · [数据口径](https://github.com/honestTai/honestTai/blob/main/data/METHODOLOGY.zh-CN.md) · [全部公开项目](https://github.com/honestTai)

<sub>历史曲线仅重建当前仍保留的 Star 与可见 Fork，并非过去每日净总量。每日实测总量自 2026-10-06 开始，不伪造回填。</sub>

---

<div align="center">

Made by **[honestTai](https://github.com/honestTai)** · [HRouter](https://hrouter.net/home) · [更多项目](https://github.com/honestTai)

</div>

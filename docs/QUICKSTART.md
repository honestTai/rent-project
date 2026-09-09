# 本地快速开始

本流程适合源码开发。服务部署与反向代理见[部署说明](DEPLOYMENT.md)。

## 1. 准备环境

| 组件 | 要求 |
| --- | --- |
| Java | 源码目标 JDK 8；本次发布使用 JDK 17 完成编译与测试 |
| Maven | 3.6+，需要加入 PATH；仓库不提供完整 Maven Wrapper |
| Node.js | 22；npm workspace，依赖按 package-lock.json 安装 |
| MySQL | 8.0，使用本项目独立数据库 |
| Redis | 7.x，网关和中台使用同一 Redis 数据库 |
| Python | 3.10+，用于初始化；AI Agent 为可选服务 |

Maven 默认从官方 Maven Central 解析依赖和插件，项目 POM 不固定第三方镜像。国内网络如需镜像，可按 [Maven 官方镜像配置说明](https://maven.apache.org/guides/mini/guide-mirror-settings.html)在自己的 `~/.m2/settings.xml` 中配置；不要将本机镜像凭据或 `settings.xml` 提交到仓库。

## 2. 初始化新数据库

```bash
git clone https://github.com/honestTai/rent-project.git
cd rent-project
python -m pip install -r customer-deploy/scripts/requirements-init.txt
```

先阅读[初始化数据](INITIALIZATION.md)，设置独立的 `RENTAL_PRODUCT_AES_SECRET`，
再用 `customer-deploy/scripts/initialize.py` 创建新的 `equipment_platform` 和 `equipment_alipay`。
首次管理员密码由你输入；不要导入旧业务数据库或直接执行历史迁移脚本。
加 `--demo` 可在本机隔离数据库中创建虚构分类、下架商品、受限用户、3 种状态订单、分期账单、合同和零金额售后，供页面与详情演示。所有样本均有 DEMO 标识，外部交易身份和授权为空，定时任务停用；演示环境不能填写真实服务商密钥。未加此选项时业务订单和用户表为空。

## 3. 配置连接

以下环境变量需要传递给运行服务的终端或进程。数据库名不同时，覆盖对应 JDBC URL。

| 变量 | 用途 |
| --- | --- |
| `RENTAL_PRODUCT_AES_SECRET` | 与初始化工具完全相同的密钥，网关、中台和租赁服务保持一致 |
| `PLATFORM_DB_URL` | 默认本机 `equipment_platform`；中台与网关共用 |
| `PLATFORM_DB_USERNAME` / `PLATFORM_DB_PASSWORD` | 中台数据库账号与密码 |
| `ALIPAY_DB_URL` | 默认本机 `equipment_alipay` |
| `ALIPAY_DB_USERNAME` / `ALIPAY_DB_PASSWORD` | 租赁数据库账号与密码 |
| `PLATFORM_REDIS_HOST` / `PLATFORM_REDIS_PORT` / `PLATFORM_REDIS_PASSWORD` | 中台缓存，默认 `127.0.0.1:6379` |
| `GATEWAY_REDIS_HOST` / `GATEWAY_REDIS_PORT` / `GATEWAY_REDIS_PASSWORD` | 网关缓存，需与中台相同 |
| `ALIPAY_REDIS_HOST` / `ALIPAY_REDIS_PORT` / `ALIPAY_REDIS_PASSWORD` | 租赁服务缓存 |
| `ZHONGTAI_BASE_URL` | 租赁服务访问中台，local 默认 `http://127.0.0.1:7780` |

PowerShell 使用 `$env:变量名 = '值'`；Bash 使用 `export 变量名='值'`。
真实配置仅放在本机文件、环境变量或密钥管理服务中。不要复制到 YAML 的默认值里。

## 4. 构建与启动后端

```bash
mvn -B -ntp test package
```

构建产物在各模块 `target/`。`package` 阶段会写入 prod 默认配置，因此**本地运行必须显式选择 local**。
分别打开四个终端，确认每个终端都有上一节的环境变量，按顺序执行：

```bash
java -jar equipment-eureka/target/equipment-eureka-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
java -jar equipment-platform/target/equipment-platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
java -jar equipment-alipay/target/equipment-alipay-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
java -jar equipment-gateway/target/equipment-gateway-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

每行是独立常驻进程。中台成功启动后再运行租赁服务。

| 服务 | local 端口 | 检查 |
| --- | --- | --- |
| 注册中心 | 8761 | `http://127.0.0.1:8761` |
| 中台 | 7780 | `/actuator/health` |
| 租赁 | 7781 | `/actuator/health` |
| 网关 | 7777 | `/actuator/health` |

## 5. 启动两个 Web 后台

在前端目录安装依赖，然后分别用两个终端运行：

```bash
cd equipment_management_system_fornt
npm ci
npm run dev:platform
```

```bash
cd equipment_management_system_fornt
npm run dev:rent
```

- 中台：`http://localhost:8084`
- 租赁后台：`http://localhost:8083`
- 默认网关：`http://127.0.0.1:7777`

用初始化时创建的管理员登录，先核对角色、权限和中台配置。
配置缺失时部分业务入口会显示错误或空状态，按[操作手册](USER_GUIDE.md)补齐配置。

## 6. 可选 AI Agent

Agent 不属于默认四个 Java 服务。它需要模型配置，业务查询通过网关和专用只读账号完成。

```bash
cd equipment-agent
python -m venv .venv
# 激活虚拟环境后执行
python -m pip install -e ".[dev]"
python -m equipment_agent
```

启动前设置 `AGENT_PLATFORM_BASE_URL=http://127.0.0.1:7777`、
`AGENT_GATEWAY_BASE_URL=http://127.0.0.1:7777`，在中台 `platform` 系统补齐 `agent.llm.*`。
更多变量见 [Agent README](https://github.com/honestTai/rent-project/blob/main/equipment-agent/README.md)。

## 常见问题

| 现象 | 检查方向 |
| --- | --- |
| 无法登录 / 密码错误 | 初始化密钥与三个服务是否一致，数据库是否连接到同一套新库 |
| 登录后很快失效 | 网关与中台 Redis 地址、端口、密码、database 是否一致 |
| 菜单不可见 / 403 | 用户角色、角色资源授权、API 权限，变更后重新登录 |
| 网关 5xx | 中台和租赁服务是否已监听对应 local 端口 |
| 商品同步、签约或售后报配置缺失 | 支付宝应用、密钥、类目、回调、合同和图片存储尚未配置 |
| Agent 不可用 | Python 服务 7790、模型参数和权限是否已准备 |

新初始化默认关闭业务定时任务。完成各项平台联调后，再按需要逐项启用。

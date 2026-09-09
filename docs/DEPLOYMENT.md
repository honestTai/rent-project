# Docker 安装、升级与维护

发行包适合在 Linux 上部署独立的租赁系统。包内包含四个 Java 服务 JAR、两个管理前端、Nginx 配置和初始化工具；默认启动独立 MySQL 8、Redis 与业务服务。Python Agent 需要另外部署。

从 [GitHub Releases](https://github.com/honestTai/rent-project/releases) 下载发行包及同版 `SHA256SUMS`。安装机需要 **Python 3.10+、Docker Engine 和 Docker Compose 插件 2.20+**，不需要 Java、Maven 或 Node.js。首次仍需联网拉取 Docker 镜像并构建 Python 工具镜像，本包不是离线镜像包。Docker 安装方式按发行版选择[官方说明](https://docs.docker.com/engine/install/)。

## 1. 验证下载文件

从同一次 Release 下载 `.tar.gz` 和 `SHA256SUMS` 到单独目录。以下 `VERSION` 表示下载的版本号，例如 `v0.1.0`，对应文件 `rent-project-v0.1.0-docker.tar.gz`。每个版本使用新的空工具目录；下面以 `release-tools` 为例，已有同名目录时换一个新名字。

```bash
sha256sum -c SHA256SUMS
mkdir ./release-tools
tar -xzf rent-project-VERSION-docker.tar.gz -C ./release-tools
```

核对输出为 `OK` 后再使用包内脚本，不使用 `curl | sh`。记录 `SHA256SUMS` 中该压缩包对应的 64 位摘要，后续以 `DIGEST` 表示。部署引擎还会检查包内清单、文件摘要和解压路径；摘要用于校验下载内容，不能替代对下载来源的核对。

## 2. 全新安装

默认安装将数据保存在 `/opt/rent-project`。当前用户需要能够访问 Docker，并有权创建和写入该目录；需要授权的服务器目录应由服务器管理员预先设置权限。

```bash
sh ./release-tools/install.sh ./rent-project-VERSION-docker.tar.gz \
  --sha256 DIGEST --root /opt/rent-project
```

该命令使用数据库模式 `managed`：创建本部署专用 MySQL 与 Redis，生成独立数据库密码、Redis 密码和 AES 密钥，初始化两个新库及管理员，然后启动服务并等待健康检查。安装只面向新库，不会覆盖已有业务数据库。终端隐藏输入管理员密码两次，默认账号为 `admin`；密码须为 12–128 字符，包含 `+ / =` 以外的标点，不能含首尾空白。安装器不保存管理员明文密码，请自行保存在密码管理器中。

需要本机演示样本时，在**首次安装**添加 `--demo`：

```bash
sh ./release-tools/install.sh ./rent-project-VERSION-docker.tar.gz \
  --sha256 DIGEST --root /opt/rent-project-demo --demo
```

演示环境包含明确标记的虚构商品、用户、订单和账单；支付宝客户端返回本地模拟结果，定时任务、电子签与自动扣款保持关闭。演示实例只供本机访问，不配置真实服务商凭据，不作为真实业务环境。详细范围见[初始化手册](INITIALIZATION.md)。

常用安装参数：`--admin-user` 更改管理员名，`--platform-db` / `--alipay-db` 更改两个新库名，`--http-port` 更改入口端口，`--timeout` 设置健康等待秒数（默认 360，允许 10–3600）。`--http-bind` 默认 `127.0.0.1`；演示模式只接受回环地址。同机部署多套实例时，应使用不同 `--root` 和入口端口。已有安装目录应使用升级命令，不再次运行安装。

安装完成后访问：

- 中台：`http://127.0.0.1:8080/platform/`
- 租赁后台：`http://127.0.0.1:8080/rent/`

在另一台电脑访问服务器时，不能使用自己电脑的 `127.0.0.1`。先通过服务器本机或 SSH 隧道验证，再配置自己的 HTTPS 反向代理。默认仅发布 Nginx 的回环端口，MySQL、Redis、注册中心和 Java 服务无需暴露公网。

## 3. 使用已有 MySQL / RDS

外部数据库模式只替换 MySQL；Redis 和业务服务仍在本部署的 Docker 网络运行。准备 MySQL 8、两个尚未使用的库名，以及允许创建这两个新库和表的初始化账号。不要填入已有业务库，远程数据库不支持 `--demo`。

连接地址应能从 Docker 容器到达；容器内的 `127.0.0.1` 指容器本身。RDS 应配置连接白名单或安全组，并优先通过私网连接。预先创建应用账号与初始化账号，按两个目标库授予相应权限；不要提前创建业务库本身。

```bash
sh ./release-tools/install.sh ./rent-project-VERSION-docker.tar.gz \
  --sha256 DIGEST --root /opt/rent-project \
  --db-mode external --db-host mysql.example.com --db-port 3306 \
  --db-user rent_app --init-user db_bootstrap \
  --platform-db equipment_platform --alipay-db equipment_alipay
```

替换示例数据库地址与账号。终端会隐藏读取应用数据库密码、初始化密码及管理员密码。省略 `--init-user` 时用应用账号初始化，此时该账号也必须有创建两个新库的权限。工具生成并私密保存部署 `.env`，不提供 `--config` 导入选项，也不会覆盖已有 `.env`。

外部数据库使用 `DB_SSL_MODE=VERIFY_IDENTITY`，校验证书及主机名。需要云厂商或私有 CA 时，在安装命令追加 `--ssl-ca /本机路径/mysql-ca.pem`；脚本复制到 `shared/tls/mysql-ca.pem` 并设置容器内路径 `/run/db-ca/mysql-ca.pem`。Java 信任库只包含公开 CA 证书，Python、Java 与备份客户端均使用验证连接。使用证书覆盖的数据库域名，不要为绕过主机名错误关闭校验。

初始化账号需要建库、表结构及初始数据写入权限；应用账号需要两个业务库的业务读写权限，也用于备份读取。账号实际授权由部署者在自己的数据库上设置。受控自动化环境可注入 `EMS_INIT_ADMIN_PASSWORD`、`EMS_DEPLOY_DB_PASSWORD` 和独立的 `EMS_INIT_DB_PASSWORD`；不要在命令参数中传密码。部署 `.env` 不保存独立初始化账号密码，也不保存管理员明文密码。

## 4. 目录与需要保存的数据

```text
/opt/rent-project/
├── .env                       # 本部署连接参数及稳定密钥
├── deploy-state.json           # 部署引擎状态
├── deploy-private.log          # 失败诊断，可能含凭据，权限 600
├── current -> releases/版本号
├── previous -> releases/上一版本
├── releases/                  # 校验通过的完整应用发行文件
├── shared/
│   ├── mysql/                 # managed 模式数据库数据
│   ├── redis/
│   ├── uploads/
│   ├── logs/
│   └── tls/                   # 可选数据库 CA 与 Java 信任库
└── backups/                   # 升级前备份，包含敏感信息
```

应用版本放在 `releases/`；数据库、缓存、上传与日志跨版本保存在 `shared/`。日常更新不要在 `current/` 手工覆盖 JAR、修改清单或删除旧版本。**`.env` 中 AES 密钥必须保持稳定**，它与初始化管理员及已有加密数据对应；丢失或随意更换后，恢复数据库也不等于恢复可登录环境。

备份至少保留两个 MySQL 库、上传文件及 `.env`，同时保存部署版本与配置记录。`backups/` 含账号密文、业务数据和应用密钥，应限制访问并存到受控的异机位置。应用运行中的 MySQL 数据目录不能简单复制作为可靠逻辑备份；使用部署引擎生成的 SQL 备份或数据库服务提供的备份能力，并验证恢复。

每次自动备份生成 `databases.sql.gz`（两个库）、`uploads.tar.gz`、`.env` 和记录完成状态及摘要的 `backup.json`。Redis 数据保留在原 `shared/redis`，**不包含在该备份归档中**；需要缓存持久化副本时另行安排 Redis 备份。数据库 CA、Java 信任库、发行版本和部署状态也应纳入自己的异机恢复资料。

## 5. 升级

下载新版本包及摘要，先验证下载文件，再使用新包内的升级脚本。`--root` 必须指向已安装的同一目录：

```bash
sh ./release-tools/upgrade.sh ./rent-project-VERSION-docker.tar.gz \
  --sha256 DIGEST --root /opt/rent-project
```

升级核对发行包与数据库结构版本；仅结构版本兼容时继续。随后停止 Nginx 和四个 Java 服务，备份两个数据库、`.env` 与上传文件，保留共享 MySQL / Redis 数据，切换整套应用并重建容器，等待健康检查。该过程有维护窗口，请提前安排停止业务写入；容器 `running` 不等于应用已经可用。[Docker 启动顺序说明](https://docs.docker.com/compose/how-tos/startup-order/)解释了 `service_healthy` 与仅启动容器的区别。

本工具**不自动执行 SQL 迁移，也不把初始化工具用于升级**。如果新包的 `schema_version` 不兼容，升级拒绝，应按对应版本说明先在恢复副本上验证迁移。不要修改清单或初始化标记绕过校验。

若新版本健康检查失败，部署引擎尝试恢复上一套应用发行文件。请检查失败日志与恢复结果；恢复动作本身失败时，应保持维护状态排查，不重复执行安装。健康通过后，再登录验证权限、商品、订单与上传文件等实际功能。

## 6. 回滚与数据库恢复

默认回滚到记录中的上一版本，也可以指定已保留的版本：

```bash
sh ./release-tools/rollback.sh --root /opt/rent-project
sh ./release-tools/rollback.sh --root /opt/rent-project --version 旧版本号
```

回滚切换应用文件并重新创建容器，**不会自动还原数据库或上传文件**。它适用于数据库结构仍兼容的应用回退；旧版本不一定能读取经过迁移的新结构。

需要恢复数据库时，先停止应用写入并保存当前故障现场，将备份恢复到独立 MySQL 实例或另一对库名进行验证，再同时核对应用版本、数据库、上传文件与对应 AES 密钥。不要在仍对外服务的库上直接导入旧备份。RDS 的时间点恢复及备份保留期在自己的云平台设置，应用回滚不会代替这些操作。

## 7. 入口、端口与外部服务

| 服务 | 容器内端口 | 用途 |
| --- | --- | --- |
| Nginx | 80 | 默认仅映射宿主 `127.0.0.1:8080` |
| Gateway | 9800 | 统一登录与 API 网关 |
| Platform | 9804 | 权限、配置、通知与任务 |
| Alipay | 9803 | 租赁商品、订单和交易接口 |
| Eureka | 8761 | 注册中心；自身不注册属于正常配置 |
| MySQL | 3306 | managed 模式双库 |
| Redis | 6379 | 平台及网关 DB 0，租赁 DB 1 |

Nginx 将 `/gateway/` 去掉后转发给 Gateway。以自己的 HTTPS 域名为例，完整外部路径为：

- 管理后台：`https://你的域名/platform/`、`https://你的域名/rent/`。
- 网关业务接口：`https://你的域名/gateway/api/web/**`。
- 小程序后端接口：`https://你的域名/gateway/api/rent/v1/**`。
- 支付宝通知：`https://你的域名/gateway/notify/**`，具体通知路径按业务接口配置。
- 上传文件：`https://你的域名/uploads/**`。

外层 HTTPS 反向代理应保留这些路径并正确传递原始主机、客户端 IP 与协议。若反向代理运行在另一个容器中，它的回环地址不是部署宿主机；需要明确配置共享网络或宿主可达地址。

Agent 不在默认发行运行栈中，未单独部署时 AI 请求不可用。真实支付宝、电子签、代扣、通知、存储及模型服务需要自己的应用和凭据，在中台配置并完成联调后逐项启用；容器健康检查不验证这些外部业务。

## 8. 故障排查

使用当前发行目录中的引擎检查状态、校验已安装文件或执行手动备份：

```bash
python3 /opt/rent-project/current/scripts/deploy.py check --root /opt/rent-project
python3 /opt/rent-project/current/scripts/deploy.py status --root /opt/rent-project
python3 /opt/rent-project/current/scripts/deploy.py backup --root /opt/rent-project
```

`check` 检查已安装发行文件摘要与 Compose 配置，不等于业务健康验收；`status` 显示版本及各容器状态。`backup` 会停止四个 Java 服务与 Nginx，完成备份后重新启动并等待健康，因此同样需要维护窗口。

维护时停止应用，再启动当前版本：

```bash
python3 /opt/rent-project/current/scripts/deploy.py stop --root /opt/rent-project
python3 /opt/rent-project/current/scripts/deploy.py start --root /opt/rent-project
```

`stop` 仅停止四个 Java 服务与 Nginx，MySQL、Redis、容器和持久化文件保留；它不是卸载或删除命令。`start` 校验当前发行版本后启动并等待健康，不重新初始化数据库。

Compose 失败输出保存在部署根目录的 `deploy-private.log`，权限为 `600`，终端只提示私有日志路径。**该文件可能包含密码或其他敏感配置，不要上传到 GitHub、工单或公开聊天。** 在本机结合 `deploy-state.json` 和 `shared/logs/` 排查原因；管理员明文不作为凭据文件保存，但诊断日志和备份仍须按敏感文件保管。

### 恢复未完成的首次安装

修复网络、权限或服务启动问题后，用**原版本、原 SHA-256 和原安装目录**继续首次安装：

```bash
sh ./release-tools/install.sh ./rent-project-VERSION-docker.tar.gz \
  --sha256 DIGEST --root /opt/rent-project --resume
```

`--resume` 只接受状态仍为首次安装中或首次安装失败的记录；已成功健康运行过的安装不可使用它。恢复沿用现有 `.env` 中的库名、连接参数、演示选项及全部密钥，新的安装连接选项不会替换这些配置，不会重新生成密钥。

若部署记录已确认初始化成功，恢复时不再调用初始化器；若尚未记录成功，则由原初始化标记保护：同版、同校验和、同库名对和演示选项且完成的库只读校验后返回，半完成或不匹配的库仍会拒绝。需要重新输入密码时，请使用首次设置的管理员密码；已有完成账号不会被重置。不要删除数据库或改写初始化标记来绕过拒绝。

`start` 用于启动已经选定的当前应用版本，不代替首次安装的 `--resume`。常规版本更新使用 `upgrade`，应用回退使用 `rollback`。

| 现象 | 检查顺序 |
| --- | --- |
| 摘要或清单校验失败 | 是否从同一 Release 下载包和 SHA256SUMS；重新下载原包，不修改清单绕过校验。 |
| 找不到 Compose 或 `--wait` 不支持 | 使用 Compose 插件 2.20+，不是旧 `docker-compose`；按官方安装文档更新。 |
| 镜像拉取 / 工具镜像构建失败 | 安装机到镜像仓库和依赖源的网络、代理、磁盘空间。包本身不含全部镜像。 |
| 新库初始化被拒绝 | 目标库已存在、库名对不一致、权限不足或标记不匹配；首次安装可按上述条件用原包 `--resume`，半完成库仍须排查，不重放 seed SQL。 |
| MySQL 连接超时 | 容器到数据库的 DNS、端口、RDS 白名单、安全组及私网路由。 |
| TLS 主机名或证书错误 | 使用证书覆盖的域名，检查 CA 文件及容器挂载路径；不要直接关闭验证。 |
| 健康检查超时 / 页面 502 | 查看部署输出及 shared/logs，检查内存、磁盘、数据库、Redis、Eureka 与启动错误。 |
| 管理员登录失败 | 首次设置的账号密码、当前库对，以及与初始化一致的 AES 密钥。 |
| 页面正常但图片 404 | shared/uploads、Nginx /uploads/ 路径及中台上传根目录/访问前缀。 |
| 结构版本不兼容 | 按该版迁移说明验证；初始化工具不能升级已有库。 |
| 回滚后仍有新数据 | 回滚仅切换应用；数据库与上传文件保持当前状态，这是预期行为。 |

发送经过检查的错误摘要前删除密码、令牌、连接凭据和客户资料。不要公开 `deploy-private.log`、`.env`、数据库 SQL、`backups/` 或整个运行目录。密钥保管与问题报告入口见[安全说明](../SECURITY.md)；公开历史、截图和 Actions 的检查范围及限制见[公开内容复查记录](SECURITY_REVIEW.md)。该记录不代替应用安全审计，新发行包需要单独检查。

## 从源码制作发行包

开发机需要 Git、Python 3.10+、JDK 17、Maven、Node.js 22、npm 和可用 Docker。先提交希望发布的代码并完成测试，再构建指定 Git 提交：

```bash
python tools/build_release.py --version v0.1.0 --ref HEAD --output dist-release
```

输出 `dist-release/rent-project-v0.1.0-docker.tar.gz` 和 `SHA256SUMS`。构建器只从 `--ref` 的已提交 Git 快照导出源码，不包含未提交修改或本机忽略的照片、密钥和运行数据；它不替代提交前安全检查。构建阶段跳过 Java 单元测试，因此应事先完成该提交的测试或 CI 验证。

包内 `SOURCE_COMMIT`、`manifest.json` 和 `images.lock.json` 记录源码、文件摘要及基础镜像摘要。前端路径在构建中固定为 `/platform/`、`/rent/` 和 `/gateway`，与包内 Nginx 一致。不要混用不同版本的前端和 JAR。源码运行方式见[快速开始](QUICKSTART.md)。

发行部署使用 `customer-deploy/` 中的工具；仓库根 `docker-compose.local.yml` 是部分开发服务配置，不是完整安装入口。Compose 的重建与等待行为见[官方 `docker compose up` 文档](https://docs.docker.com/reference/cli/docker/compose/up/)。

# Docker 发行包

本目录是独立发行包的部署模板。普通部署者从 [GitHub Releases](https://github.com/honestTai/rent-project/releases) 下载编译好的 `.tar.gz` 和同版 `SHA256SUMS`；无需在服务器安装 Java、Maven 或 Node.js。完整流程见[安装、升级与回滚手册](https://honesttai.github.io/rent-project/DEPLOYMENT/)。

要求：Linux、Python 3.10+、Docker Engine、Docker Compose 插件 2.20+。首次需要联网拉取官方 Docker 镜像并构建 Python 工具镜像，本包不是离线镜像包。Docker 安装按[官方文档](https://docs.docker.com/engine/install/)进行，部署脚本不会代为安装系统服务。

## 下载、校验与安装

以下 `VERSION` 使用 Release 附件中的真实版本，`DIGEST` 使用 `SHA256SUMS` 中该压缩包对应的 64 位 SHA-256。每个版本解压到新的空目录，已有 `release-tools` 时换一个目录名：

```bash
sha256sum -c SHA256SUMS
mkdir release-tools
tar -xzf rent-project-VERSION-docker.tar.gz -C release-tools
sh release-tools/install.sh ./rent-project-VERSION-docker.tar.gz \
  --sha256 DIGEST --root /opt/rent-project
```

例如 `VERSION=v0.1.0` 对应 `rent-project-v0.1.0-docker.tar.gz`。先确认校验输出为 `OK` 再执行脚本，不使用 `curl | sh`。默认 `managed` 模式自动建立独立 MySQL 8、Redis、两个新业务库及管理员，再启动四个 Java 服务和 Nginx。管理员密码隐藏输入两次，不保存明文；其他部署密码与 AES 密钥独立生成并保存在私有 `.env`。

默认入口仅在本机开放：`http://127.0.0.1:8080/platform/` 与 `http://127.0.0.1:8080/rent/`。需要虚构样本时，首次安装添加 `--demo` 并使用独立目录；演示模式只供本机，不配置真实支付、电子签或其他服务商凭据。

首次安装中断后，先在本机排查原因，再用相同包、相同摘要和原目录执行：

```bash
sh release-tools/install.sh ./rent-project-VERSION-docker.tar.gz \
  --sha256 DIGEST --root /opt/rent-project --resume
```

`--resume` 仅用于同版同摘要的未完成首次安装，沿用原 `.env` 参数及密钥；已成功健康运行的安装不能恢复初始化。已记录初始化成功时不再运行初始化器，否则由原完成标记检查保证：匹配完成的库只读返回，半完成或不匹配的库拒绝。若再次询问管理员密码，应输入最初设置的密码，已有账号不会被重置。

外部 MySQL / RDS 使用 `--db-mode external --db-host 数据库域名 --db-user 应用账号`，可通过 `--init-user 初始化账号` 分离建库账号，并用 `--ssl-ca /本机路径/mysql-ca.pem` 提供私有 CA。密码隐藏输入，不支持 `--config`；目标必须是两个新库，远程 TLS 验证主机名，外部模式不支持演示数据。连接参数与账号权限见[部署手册](https://honesttai.github.io/rent-project/DEPLOYMENT/)。

## 升级和回滚

下载新包并完成同样的摘要验证，使用新包中的工具操作原安装目录：

```bash
sh release-tools/upgrade.sh ./rent-project-VERSION-docker.tar.gz \
  --sha256 DIGEST --root /opt/rent-project
sh release-tools/rollback.sh --root /opt/rent-project
# 也可指定保留的应用版本
sh release-tools/rollback.sh --root /opt/rent-project --version 旧版本号
```

升级检查包内清单及结构版本，停止应用写入后备份两个数据库、上传目录和 `.env`，再重建应用容器并等待健康检查。MySQL / Redis 的共享数据保留；本工具不自动迁移 SQL，不使用空库初始化来升级已有系统。新应用不健康时尝试恢复上一版，仍需检查最终恢复状态。

**回滚只切换应用，不还原数据库或上传文件。** 需要恢复数据库时，先在独立环境验证 SQL 备份、上传数据、应用版本和 AES 密钥，再安排维护窗口恢复。不要删除共享数据或重新初始化来处理升级失败。

## 持久化与包内容

部署根目录保存 `.env`、`deploy-state.json`、`current` / `previous` 链接、`releases/<版本>`、`shared/` 与 `backups/`。数据库、Redis、上传和日志位于 `shared/`，跨应用升级保留；`.env` 中 AES 密钥必须稳定保存。

发行归档根目录包含 `manifest.json`、Compose 配置、`jars/`、`www/`、`sql/`、`scripts/`、`nginx/` 与命令包装脚本。引擎按清单检查文件内容，不从用户的运行目录打包数据库、密钥、日志或上传内容。

`scripts/initialize.py` 仅初始化新库，不提供覆盖、清库或升级选项；不要直接运行 `mysql < sql/init.sql`，也不要在业务库重放 `seed-*.sql` / `demo.sql`。初始化的结构和管理员行为见[初始化手册](https://honesttai.github.io/rent-project/INITIALIZATION/)。

备份目录包含两个库的 `databases.sql.gz`、`uploads.tar.gz`、`.env` 和 `backup.json`；Redis 仅原位保留，不在此归档中。CA、发行版本与部署状态也要另行妥善保存。

已安装后可运行 `python3 /opt/rent-project/current/scripts/deploy.py status --root /opt/rent-project` 查看健康状态；`check` 校验发行文件及配置，`backup` 会停应用、备份并重新启动，`stop` 仅停应用并保留 MySQL / Redis 和全部数据，`start` 启动当前版本并等待健康、不重新初始化。没有自动卸载或数据库恢复命令。

Compose 失败的原始输出写入部署根目录 `deploy-private.log`，权限为 `600`；该文件可能含敏感配置，仅在本机排查，不能上传到 GitHub、工单或公开聊天。`shared/`、`backups/`、`.env` 同样属于私有运行数据。密钥保管与问题报告入口见[安全说明](https://github.com/honestTai/rent-project/blob/main/SECURITY.md)。

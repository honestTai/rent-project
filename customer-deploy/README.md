# 独立部署包

本目录包含注册中心、中台、租赁业务、网关、两个管理前端及 Redis 的部署配置。数据库使用独立 MySQL 8；入口端口及网络以 Compose 配置为准。

## 文件

- `scripts/initialize.py`：仅初始化两个新库；没有覆盖或清库选项。
- `sql/init.sql`：37 张业务表结构，由程序按数据库分段执行。
- `sql/seed-platform.sql`：权限、空配置及停用的 7 项内置任务。
- `sql/seed-alipay.sql`：通用分类和空业务配置。
- `sql/demo.sql`：可选的虚构商品、受限用户、订单、分期账单、合同和零金额售后。
- `jars/`：四个服务 JAR；`www/platform/`、`www/rent/`：前端构建结果。
- `uploads/`、`logs/`、`data/redis/`：运行数据，不能提交到公开仓库。

## 初始化

完整说明见 [初始化手册](../docs/INITIALIZATION.md)。从本目录运行：

```bash
python3 -m venv .venv-init
. .venv-init/bin/activate
python -m pip install -r scripts/requirements-init.txt
python scripts/initialize.py --host 127.0.0.1 --user db_bootstrap \
  --platform-db equipment_platform --alipay-db equipment_alipay
```

以上命令只读预检，数据库密码隐藏输入。正式创建时，先向当前环境注入与后端一致的 `RENTAL_PRODUCT_AES_SECRET`，再执行：

```bash
python scripts/initialize.py --host 127.0.0.1 --user db_bootstrap \
  --platform-db equipment_platform --alipay-db equipment_alipay \
  --execute --confirm-new-databases equipment_platform,equipment_alipay
```

管理员默认名为 `admin`，密码由操作人设置，无公开默认密码。需要演示样本时，在本机隔离环境的首次命令添加 `--demo`；该实例不可填写真实服务商密钥。小程序 JWT 密钥由初始化程序随机生成并保存到平台配置，不需要另设环境变量。不支持 `mysql < sql/init.sql`。

## 启动

1. 复制 `.env.example` 为 `.env`，填写数据库、缓存及应用密钥，权限设为 `600`。AES 密钥与初始化使用值必须一致。
2. 放入四个 JAR 和两套前端构建产物。
3. 按 [Docker 官方文档](https://docs.docker.com/engine/install/) 安装 Docker Engine 与 Compose 插件。
4. 执行 `sh install.sh --check` 验证部署文件，再执行 `sh install.sh` 启动服务。脚本不执行数据库初始化，也不自动安装系统软件。
5. 默认从本机访问 `http://127.0.0.1:8080/platform/`、`http://127.0.0.1:8080/rent/`，确认登录与权限菜单，再按操作手册填写服务商配置、联调后启用需要的任务。默认网络独立，不需要预建共享入口网络；公网访问由自己的 HTTPS 反向代理接入。

```bash
docker compose ps
docker compose logs --tail=100 equipment-gateway equipment-platform equipment-alipay
```

数据库初始化成功不代表支付宝支付、免押、合同或通知已接通；这些能力需要部署者自己的应用、资质和环境配置。演示商品默认下架且不公开，没有真实订单或支付记录。

初始化失败会保留新库用于排查；请勿用旧版含 `DROP TABLE` 的 SQL 重试，不要以生产库为初始化目标。

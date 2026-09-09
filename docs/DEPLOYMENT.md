# 部署与维护

这里使用 Linux、Docker Compose 和外部 MySQL 8（可使用自己的 RDS）。
部署包包含 Redis、四个 Java 服务和 Nginx。Python Agent 按需独立部署。

## 打包源码

Windows 上安装 JDK、Maven、Node.js，将 Maven 加入 PATH，在根目录执行：

```powershell
cd equipment_management_system_fornt
npm ci
cd ..
powershell -File customer-deploy/package-customer.ps1
```

脚本只构建本地文件，生成 `customer-deploy/jars/` 和 `customer-deploy/www/`。
先完成 `mvn test` 再打包；打包脚本使用 `-DskipTests`。

Linux/macOS 可执行 `mvn -B -ntp test package`，将四个模块的 jar 复制到 `customer-deploy/jars/`，
分别重命名为 `equipment-eureka.jar`、`equipment-platform.jar`、`equipment-alipay.jar`、`equipment-gateway.jar`。

前端分别构建，环境变量随单次构建传入：

```bash
cd equipment_management_system_fornt
npm ci
VITE_BASE_URL=/platform/ VITE_GATEWAY_ORIGIN=/gateway npm run build:platform
VITE_BASE_URL=/rent/ VITE_GATEWAY_ORIGIN=/gateway VITE_PLATFORM_ORIGIN= VITE_PLATFORM_PUBLIC_PATH=/platform/ npm run build:rent
```

将两个 app 的 `dist/` 内容分别复制到 `customer-deploy/www/platform/` 和 `customer-deploy/www/rent/`。

## 初始化与配置

1. 将部署包复制到你自己的服务器目录。
2. 按[初始化数据](INITIALIZATION.md)创建**新的独立数据库**；不要在已有库执行建库 SQL。
3. 从 `.env.example` 复制 `.env`，填写 RDS/MySQL、Redis、数据库名和独立密钥，设置文件权限 `600`。
4. `RENTAL_PRODUCT_AES_SECRET` 必须与初始化管理员时相同，运行后应稳定保存。
5. Nginx 默认绑定 `127.0.0.1:8080`，接入你自己的 HTTPS 反向代理。

```bash
cd customer-deploy
cp .env.example .env
chmod 600 .env
# 完成配置后
sh install.sh
docker compose ps
```

本部署不要求加入任何既有共享 Docker 网络。需直接开放入口时，明确设置 `HTTP_BIND` 与防火墙规则；
MySQL、Redis、注册中心和 Java 服务不需要映射公网端口。

## 访问与外部平台

- 中台：`http://127.0.0.1:8080/platform/`
- 租赁运营：`http://127.0.0.1:8080/rent/`
- 网关前缀：`/gateway/`

支付宝回调域名指向网关的 `/notify/**`，小程序接口通过 `/api/rent/v1/**` 转发。
公网入口使用自己的 HTTPS 域名，并在支付宝控制台配置对应白名单与回调。
在中台填写应用、密钥、类目、存储、合同主体和通知参数后，按实际权限完成联调。

## 验证

查看 `docker compose ps` 及各服务启动日志，确认数据库、Redis、注册中心正常。
在浏览器登录，依次检查中台概览、角色权限、商品列表和订单列表。
真实支付、退款、扣款、合同签署与支付宝回调需要在部署者授权的环境单独验收。

## 更新、备份与恢复

- 更新前保存当前源码提交、镜像版本、jar、前端文件、`.env`、上传目录和数据库备份。
- 备份两个 MySQL 库并测试恢复；RDS 自动备份策略在自己的云控制台配置。
- 数据库升级使用单独审查过的增量迁移，不重跑空库初始化工具来升级已有库。
- 替换 jar/前端后运行 `docker compose up -d`，验证健康和登录链路。
- 应用回退恢复上一版构建文件；数据库回退单独处理，不能假设旧 jar 能读取新结构。

日志位于 `customer-deploy/logs/`，上传文件位于 `customer-deploy/uploads/`。
这些目录和 `.env` 不属于公开源码，不要加入提交或公开文档。

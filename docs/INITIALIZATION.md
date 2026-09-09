# 数据库初始化手册

此流程面向全新部署，使用 Python 3.10+ 和 MySQL 8。初始化程序不会清库、重置账号或迁移已有系统。两个库名必须不同，且是 3–48 位小写字母、数字或下划线，以字母开头。

## 初始化内容

| 内容 | 默认创建 |
| --- | --- |
| 平台库 | 12 张业务表、权限元数据、151 项空或安全默认配置 |
| 租赁库 | 25 张业务表、3 个通用分类、14 项空业务配置 |
| 管理员 | 操作人设置密码的一个 `admin` 账号及超级管理员角色 |
| 定时任务 | 保留全部 7 项内置任务，均停用；自动扣款、通知与电子签默认关闭 |
| 演示内容 | 使用 `--demo` 时创建 2 商品 / 2 规格 / 2 受限用户 / 3 订单 / 5 分期计划 / 5 账单 / 2 合同 / 1 零金额售后及快照 / 3 操作日志 |
| 初始化标记 | 每库另建 `ems_initialization`，记录版本、SQL 校验和、目标库对、演示选项及完成状态 |

没有预置真实客户、手机号、地址、订单、账单、支付记录、合同、回调、上传图片或服务商密钥。演示价格单位为分，商品和规格只用于熟悉后台录入。

## 1. 安装工具

在仓库根目录执行。Linux/macOS 示例：

```bash
python3 -m venv .venv-init
. .venv-init/bin/activate
python -m pip install -r customer-deploy/scripts/requirements-init.txt
```

Windows PowerShell：

```powershell
py -3 -m venv .venv-init
.\.venv-init\Scripts\python.exe -m pip install -r customer-deploy/scripts/requirements-init.txt
```

后续 Windows 命令可将 `python` 替换为 `.\.venv-init\Scripts\python.exe`。依赖为 PyMySQL 与 cryptography。使用 MySQL 8，不支持以 MariaDB、SQLite 替代执行。

## 2. 准备连接与应用密钥

准备能够创建两个新库及其表、索引和初始记录的 MySQL 初始化账号；应用运行账号的权限单独配置。两个目标库应当尚不存在。默认本地库名为 `equipment_platform` 与 `equipment_alipay`；使用其他库名时同步修改应用数据库 URL 或 Compose 的 `RDS_PLATFORM_DATABASE` / `RDS_ALIPAY_DATABASE`。

数据库密码默认由终端隐藏输入，也可由运行环境注入 `EMS_INIT_DB_PASSWORD`。管理员密码同样隐藏输入，或在受控执行环境中注入 `EMS_INIT_ADMIN_PASSWORD`。不要把密码放在命令参数、共享聊天、终端日志或 SQL 文件中。

创建管理员需要环境变量 `RENTAL_PRODUCT_AES_SECRET`，至少 32 个字符，必须与后端进程使用的值完全一致。请在本地密码管理器中生成、保存并注入。小程序 JWT 密钥由初始化程序使用安全随机数生成，作为 `alipay / miniapp.jwt.server-secret` 保存到平台配置中，不输出、不写入公开文件；重复运行不会旋转它，不需要另设 `JWT_SERVER_SECRET`。

远程 MySQL 默认启用校验证书及主机名的 TLS；私有 CA 使用 `--ssl-ca /path/to/ca.pem`。仅 `localhost`、`127.0.0.1`、`::1` 默认不启用 TLS。本工具不读取或执行 `.env`，若已在部署文件中保存 AES 密钥，应通过环境注入方式传入同一值。

## 3. 只读预检

```bash
python customer-deploy/scripts/initialize.py \
  --host 127.0.0.1 --port 3306 --user db_bootstrap \
  --platform-db equipment_platform --alipay-db equipment_alipay
```

预检检查 MySQL 版本、目标库和初始化标记，不创建库或写数据，也不需要管理员密码或 AES 密钥。预检不证明账号具备所有建表权限；写入权限不足将在正式执行时中止。

## 4. 创建新库及管理员

```bash
python customer-deploy/scripts/initialize.py \
  --host 127.0.0.1 --port 3306 --user db_bootstrap \
  --platform-db equipment_platform --alipay-db equipment_alipay \
  --execute --confirm-new-databases equipment_platform,equipment_alipay
```

`--confirm-new-databases` 必须与两个参数组成的库名对完全一致。管理员默认名为 `admin`，可用 `--admin-user myadmin` 更改。密码须为 12–128 个字符，无首尾空白，并至少包含一个 `+ / =` 以外的标点字符，以兼容现有后台密码识别逻辑。

需要页面演示样本时，在首次预检与创建命令中均添加 `--demo`。该选项只允许连接本机回环地址，应使用独立演示实例，不能填写真实服务商密钥。默认不加该选项时商品、客户、订单、账单及合同表为空。

`--demo` 同时将 `alipay.demo-mode.enabled` 设为 `true`，显示“功能导览”，并使支付宝客户端包装层返回明确标记为 DEMO 的本地模拟响应。普通初始化仍为 `false`。演示模式允许小程序通过虚构用户登录，不验证真实支付宝授权码，因此演示应用也应只供本机访问，不能开放给公网或承载真实业务；数据库连接的回环限制不会自动约束应用监听地址。电子签、代扣和定时任务仍保持关闭，所有真实服务商凭据留空。

演示商品 `status=0`、`is_public=2`，演示用户 `is_blocked=1`。订单编号统一以 `DEMO-ORDER-` 开头，分别展示关闭、完结和租赁中状态；已支付账单和已完成合同只是虚构界面状态，没有真实付款或电子签。售后金额为零。全部外部用户 ID、openid、支付宝订单 ID、冻结授权号、交易号、签署流程 ID 均为空，不创建代扣协议。不要对样本执行同步、扣款、退款、合同回传或其他真实业务操作；需要接入真实业务时另外初始化不带 `--demo` 的新库。

## 5. 重复执行与失败处理

- 两个目标库不存在：仅带 `--execute` 且库名对确认匹配才创建。建库不使用 `IF NOT EXISTS`，防止预检后同名库被其他进程创建而误用。
- 两个库已完成同版本、同 SQL 校验和、同库名对及演示选项的初始化：再次运行只读校验后成功退出，不改变用户、密码、配置或商品。重跑须保留首次的 `--demo` 选择。
- 已有库缺少正确完成标记、只有一个库存在、SQL 版本改变或演示选项不同：立即拒绝，无覆盖选项。
- 中途失败：MySQL DDL 会隐式提交，新建的库及部分表可能保留；种子数据和管理员创建在事务中回滚。程序不会自动删除库。检查错误码及服务器日志后，用另一对新库名重新验证。已有业务库应走独立的备份和迁移流程。

`sql/init.sql` 由程序按分段标记选择目标库，不可直接执行 `mysql < sql/init.sql`。`seed-*.sql`、`demo.sql` 也仅在新库初始化内部使用，不要对已运行的库手动重放。

## 6. 启动后验收

1. 应用数据库 URL 指向这两个库，AES 密钥与初始化一致。
2. 登录 `/platform/`，检查用户、角色、配置中心和任务管理。
3. 访问 `/rent/`；默认订单、账单、客户和合同列表为空；使用 `--demo` 时应能看到表中列出的 DEMO 样本与下架商品。
4. 检查全部 7 项定时任务停用；配置支付宝、电子签、通知、对象存储和模型服务，联调后逐项启用。
5. 核对 `withhold.scheduler.enabled=false`，示例环境不要启用自动扣款。

目前密码存储兼容现有 AES 可逆密文：`Aes.java` 用 SHA-256 派生 16 字节密钥后执行 AES/ECB/PKCS5Padding；名为 `Md5.md5String` 的方法实际返回原字符串。初始化按同算法生成密文，未假称采用 BCrypt。更换算法或 AES 密钥需同步迁移账号及相关加密数据，不属于初始化重跑功能。

## 验证范围

运行 `python customer-deploy/scripts/check_initialization.py` 可离线检查 SQL 分段、实体映射、任务默认停用、敏感配置留空、演示数据范围和既有数据库拒绝逻辑。离线检查不能替代 MySQL 导入及登录联调。

本次已在任务专属、只绑定本机的 MySQL 8.0.46 隔离实例验证：空业务初始化、含演示数据初始化、重复运行不改数据或密码/JWT、不同部署 JWT 独立随机、错误演示选项拒绝、已有无标记数据库拒绝、全任务停用及无外部授权。10 项离线检查通过；Python 管理员密文与实际 Java AES/MD5 代码在 ASCII、中文和 emoji 输入上一致，缺密钥时失败，JVM 密钥备用入口也通过验证。这些结果不代表支付宝、电子签或其他外部业务已经联调。

# 参与贡献

先用 Issue 描述可复现的问题或具体需求，再提交范围清楚的 Pull Request。
本仓库自有源码采用 AGPL-3.0-only；提交贡献表示你有权提交，并按同一协议提供这些修改。
第三方代码请保留原始版权、许可全文和来源说明。

## 开发与验证

运行环境、启动顺序见 [快速开始](docs/QUICKSTART.md)。

```bash
mvn -B -ntp test
cd equipment_management_system_fornt
npm ci
npm run build
npm run test:agent
```

Python Agent 的测试在 `equipment-agent` 目录执行：

```bash
python -m pip install -e ".[dev]"
python -m pytest -q
```

修改 SQL 时同步核对实体、权限资源和初始化结果；只在独立测试库验证。
提交前检查 `git diff --cached`，不要提交真实账号、密钥、服务器信息、业务数据或未脱敏截图。
说明实际跑过的检查，以及依赖外部服务而未验证的部分。

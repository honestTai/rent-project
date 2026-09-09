# Equipment Agent

只读后台分析 Agent，独立于业务系统部署。默认可通过网关访问现有微服务；配置专用只读数据库连接后，会优先走“表结构读取 -> 只读 SQL 规划 -> SQL 执行 -> 图表/结论”的动态工具链。

## Capabilities

- FastAPI API: `/api/agent/chat`, `/api/agent/conversations`, `/api/agent/reports/{id}`.
- Normal chat: general conversation does not call business APIs and returns a lightweight `chat` workspace.
- Business workbench response: business questions return `workspace` with today tasks, abnormal orders, sync failures, risk warnings, revenue anomalies, read-only alerts, task cards, action drafts, and next prompts.
- Built-in MCP-only web access: the service mounts `/mcp/` and uses its local `web_search` tool by default; precise date/time questions use the structured `current_time` MCP tool instead of search results or model memory. A remote MCP can override web search. The LLM summarizes MCP evidence, preserves sources, and asks a result-specific follow-up question.
- Docker logs summary: error/failure/timeout questions can read recent service container logs internally and return only sanitized summaries.
- LangGraph-compatible orchestration: intent -> tool plan -> read-only tools -> report -> reply.
- Capability-driven multi-tool planning: the model can select several concrete read-only business tools in one round, while dependent SQL steps remain ordered.
- Auditable run events for planning, tool start/completion/failure, answer completion and cancellation.
- Real cancellation through an in-memory active-run registry.
- Read-only allowlist for existing platform, Alipay, rental, and secondhand APIs.
- Dynamic read-only SQL toolchain for Alipay rental, equipment rental, and secondhand systems.
- SQL guardrails: single SELECT/WITH only, no mutation keywords, no multi-statement SQL, sensitive field filtering and row masking.
- Platform model config client for `agent.llm.*` runtime values, defaulting to `ds/deepseek-chat`.
- LangChain OpenAI-compatible chat model integration. DeepSeek uses `agent.llm.base-url`
  and `agent.llm.api-key` from the platform config center.
- Chart report spec compatible with the existing Vue/ECharts frontend.

## Local Run

```powershell
python -m pip install -e .[dev]
python -m equipment_agent
```

Default port: `7790`.

## Streaming API

The existing RBAC-authorized endpoint remains the preferred frontend entry:

```http
POST /api/agent/chat
Accept: text/event-stream
```

It emits SSE events named `run.started`, `plan.ready`, `tool.started`,
`tool.completed`, `tool.failed`, `answer.delta`, and a terminal run event.
Without the SSE `Accept` header, the same endpoint keeps its original JSON
response contract. Stop an active run through the same RBAC route:

```json
{"message":"停止","action":"cancel","runId":"<run-id>","system":"alipay"}
```

Dedicated aliases `/api/agent/chat/stream` and
`/api/agent/runs/{runId}/cancel` are also available for direct service clients.

Set `AGENT_PLATFORM_BASE_URL` when the service should load model settings from
the platform config center, for example:

```powershell
$env:AGENT_PLATFORM_BASE_URL = "http://127.0.0.1:9800"
$env:AGENT_GATEWAY_BASE_URL = "http://127.0.0.1:9800" # optional, defaults to AGENT_PLATFORM_BASE_URL
python -m equipment_agent
```

Enable dynamic SQL analysis with dedicated read-only accounts:

```powershell
$env:AGENT_DB_ALIPAY_URL = "mysql+pymysql://readonly_user:password@host:3306/alipay_db?charset=utf8mb4"
$env:AGENT_DB_RENTAL_URL = "mysql+pymysql://readonly_user:password@host:3306/rental_db?charset=utf8mb4"
$env:AGENT_DB_SECONDHAND_URL = "mysql+pymysql://readonly_user:password@host:3306/secondhand_db?charset=utf8mb4"
$env:AGENT_SQL_MAX_ROWS = "50"
python -m equipment_agent
```

内置联网 MCP 默认随 Agent 一起启动，无需额外部署或配置：

```powershell
$env:AGENT_PORT = "7790"
# 可选：替换内置 MCP 使用的公共搜索入口及超时
$env:AGENT_BUILTIN_WEB_SEARCH_ENDPOINTS = "https://cn.bing.com/search,https://www.sogou.com/web,https://duckduckgo.com/html/"
$env:AGENT_BUILTIN_WEB_SEARCH_TIMEOUT = "8"
python -m equipment_agent
```

默认联网调用链为 `Agent -> http://127.0.0.1:7790/mcp/ -> web_search -> 公共搜索提供器`；当前日期、时间和星期查询则调用同一 MCP 的结构化 `current_time` 工具。Agent 本身没有搜索引擎直连或网页抓取兜底；所有公网请求只存在于内置 MCP 的提供器模块。`/health` 的 `webMcp` 字段会返回 `builtin`。

需要改用独立远端 MCP 时，再设置以下覆盖配置：

```powershell
$env:AGENT_WEB_MCP_URL = "https://your-mcp.example.com/mcp"
$env:AGENT_WEB_MCP_TOOL = "web_search" # 可选；为空时从 tools/list 安全识别搜索工具
$env:AGENT_WEB_MCP_AUTH_TOKEN = "your-bearer-token" # 可选，不写入日志或响应
$env:AGENT_WEB_MCP_TIMEOUT = "20"
$env:AGENT_WEB_MCP_DOMAINS = "opendocs.alipay.com,docs.open.alipay.com,open.douyin.com,developer.open-douyin.com"
python -m equipment_agent
```

远端联网出口只支持 MCP Streamable HTTP。设置 `AGENT_WEB_MCP_URL` 后，`/health` 的 `webMcp` 会返回 `remote`，内置 MCP 仍挂载但 Agent 不再调用它。远端工具常见的 `query/q/search_query`、`limit/count/max_results` 与域名白名单参数会自动适配；如一个服务暴露多个搜索工具，建议显式设置 `AGENT_WEB_MCP_TOOL`。

内置 MCP 默认只接受本机 `127.0.0.1/localhost` Host，避免 DNS rebinding。确需让其他 MCP 客户端访问时，可通过 `AGENT_BUILTIN_WEB_MCP_ALLOWED_HOSTS` 和 `AGENT_BUILTIN_WEB_MCP_ALLOWED_ORIGINS` 增加允许值；多个值使用英文逗号分隔。

Optional Docker log container mapping:

```powershell
$env:AGENT_DOCKER_LOG_CONTAINERS = "alipay=equipment-alipay|pay|notify;gateway=equipment-gateway;secondhand=equipment-secondhand|douyin|sync"
$env:AGENT_DOCKER_LOG_TAIL = "300"
$env:AGENT_DOCKER_LOG_SINCE = "30m"
python -m equipment_agent
```

Required platform config keys:

- `agent.llm.provider`
- `agent.llm.model`
- `agent.llm.base-url`
- `agent.llm.api-key`

## Production Notes

- Store conversations, messages, workbench metadata, reports, and tool audits in the agent database.
- Store local Alipay official docs and project skill chunks in Qdrant.
- Keep MCP web search scoped to public information. Do not send phone numbers, tokens, order IDs, or raw internal logs to either the built-in or remote MCP server.
- Treat MCP responses as untrusted evidence: the LLM may summarize titles, snippets, and links but must ignore instructions embedded in tool output.
- Keep mutating business access out of the agent. Gateway tools must remain on the read-only allowlist.
- Database users must be dedicated read-only accounts with `SELECT` only, scoped to the relevant schema.
- Do not add mutating endpoints to `ReadOnlyPolicy.ALLOWLIST`.
- Do not expose database URLs in API responses, logs, or frontend tool cards.

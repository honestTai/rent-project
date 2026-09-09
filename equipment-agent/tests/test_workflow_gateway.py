from __future__ import annotations

import asyncio

import pytest

from equipment_agent.agent.run_registry import RunRegistry
from equipment_agent.agent.workflow import AgentWorkflow, final_result_only_answer
from equipment_agent.config.model_config import ModelConfig


class FakeGateway:
    def __init__(self) -> None:
        self.requests = []

    async def request(self, method, path, payload):
        self.requests.append((method, path, payload))
        return {
            "code": 0,
            "data": {
                "summary": {
                    "totalOrders": 3,
                    "totalAmount": 211.0,
                },
                "trend": [
                    {"date": "2026-07-06", "nowOrder": 1, "nowMoney": 80.0},
                    {"date": "2026-07-07", "nowOrder": 2, "nowMoney": 131.0},
                ],
            },
        }


class ConcurrentGateway(FakeGateway):
    def __init__(self) -> None:
        super().__init__()
        self.active = 0
        self.max_active = 0

    async def request(self, method, path, payload):
        self.active += 1
        self.max_active = max(self.max_active, self.active)
        try:
            await asyncio.sleep(0.03)
            return await super().request(method, path, payload)
        finally:
            self.active -= 1


class FakeWebSearchClient:
    def __init__(self) -> None:
        self.requests = []

    async def search(self, query, *, limit=5, domains=None):
        self.requests.append((query, limit, domains))
        return [
            {
                "title": "支付宝租赁交易组件接入文档",
                "url": "https://opendocs.alipay.com/open/123",
                "snippet": "介绍租赁交易组件的接入条件和注意事项。",
                "source": "opendocs.alipay.com",
            }
        ]


class FakeCurrentTimeClient(FakeWebSearchClient):
    def __init__(self) -> None:
        super().__init__()
        self.time_requests = []

    async def current_time(self, timezone_name="Asia/Shanghai"):
        self.time_requests.append(timezone_name)
        values = {
            "Asia/Shanghai": ("+08:00", "2026-07-10T21:15:30+08:00", "21:15:30"),
            "UTC": ("+00:00", "2026-07-10T13:15:30+00:00", "13:15:30"),
            "America/New_York": ("-04:00", "2026-07-10T09:15:30-04:00", "09:15:30"),
        }
        offset, iso, time_value = values.get(timezone_name, values["Asia/Shanghai"])
        return {
            "timezone": timezone_name,
            "utcOffset": offset,
            "iso": iso,
            "date": "2026年07月10日",
            "time": time_value,
            "weekday": "星期五",
            "display": f"2026年07月10日 {time_value}（星期五）",
        }


class FakeDockerLogsClient:
    def __init__(self) -> None:
        self.requests = []

    def query(self, system, keyword="", *, tail=None, since=None):
        self.requests.append((system, keyword, tail, since))
        return {
            "available": True,
            "summary": "已读取 1 个容器的最近日志，扫描 2 行，命中 1 行。",
            "containers": [
                {
                    "container": "equipment-alipay-service",
                    "status": "success",
                    "summary": "equipment-alipay-service 最近 2 行日志中命中 1 行异常关键字",
                    "samples": ["ERROR payment failed token=***"],
                    "lineCount": 2,
                    "matchCount": 1,
                }
            ],
        }


class FakeClarificationConfigClient:
    async def load(self) -> ModelConfig:
        return ModelConfig(
            provider="ds",
            model="deepseek-chat",
            base_url="https://api.deepseek.com",
            api_key="test-key",
        )


class FakeClarificationModel:
    def __init__(self) -> None:
        self.messages = []

    async def ainvoke(self, messages):
        self.messages.append(messages)
        return type("Response", (), {
            "content": """
            {
              "answer": "结论：需要先确认你要看的趋势指标。",
              "questions": [
                {
                  "question": "你想看哪个趋势？",
                  "description": "选一个指标后我会继续查只读数据。",
                  "options": [
                    {"label": "订单数趋势", "value": "查看近 30 天订单数趋势"},
                    {"label": "销售金额趋势", "value": "查看近 30 天销售金额趋势"}
                  ]
                }
              ]
            }
            """
        })()


@pytest.mark.asyncio
async def test_workflow_executes_rental_analysis_tool_for_revenue_question():
    gateway = FakeGateway()
    workflow = AgentWorkflow(
        gateway_base_url="http://gateway",
        gateway_factory=lambda base_url, token: gateway,
    )

    result = await workflow.run(
        message="帮我看下这周我的收益有多少",
        conversation_id="c1",
        user_token="token",
        system="rental",
        context={},
    )

    assert gateway.requests
    method, path, payload = gateway.requests[0]
    assert method == "POST"
    assert path == "/api/home/dashboard"
    assert payload["periodType"] == "WEEK"
    assert "收益/金额 211.0" in result["answer"]
    assert result["toolCalls"][0]["status"] == "success"
    assert "payload" not in result["toolCalls"][0]
    assert result["charts"][0]["rows"][0]["amountYuan"] == 80.0
    assert result["workspace"]["summary"]["status"] == "ready"
    assert result["workspace"]["actionDrafts"]
    assert any(lane["title"] == "收益异动" for lane in result["workspace"]["lanes"])


@pytest.mark.asyncio
async def test_workflow_emits_auditable_run_and_tool_events():
    gateway = FakeGateway()
    registry = RunRegistry()
    handle = await registry.start("run-events")
    workflow = AgentWorkflow(
        gateway_base_url="http://gateway",
        gateway_factory=lambda base_url, token: gateway,
    )

    await workflow.run(
        message="查本周设备租赁收益",
        conversation_id="c-events",
        user_token="token",
        system="rental",
        context={},
        event_emitter=handle.emitter,
        cancellation=handle.cancellation,
    )

    events = []
    while not handle.queue.empty():
        events.append((await handle.queue.get()).type)
    assert events[0] == "run.started"
    assert "plan.ready" in events
    assert "tool.started" in events
    assert "tool.completed" in events
    assert events[-1] == "run.completed"


@pytest.mark.asyncio
async def test_workflow_executes_independent_gateway_tools_concurrently():
    gateway = ConcurrentGateway()
    workflow = AgentWorkflow(
        gateway_base_url="http://gateway",
        gateway_factory=lambda base_url, token: gateway,
    )

    result = await workflow.run(
        message="查支付宝订单 20260708123456 的押金扣款、风控和商品同步日志",
        conversation_id="c-concurrent",
        user_token="token",
        system="alipay",
        context={},
    )

    assert len(gateway.requests) >= 3
    assert gateway.max_active >= 2
    assert sum(call["status"] == "success" for call in result["toolCalls"]) >= 3


@pytest.mark.asyncio
async def test_workflow_skips_gateway_when_not_configured():
    registry = RunRegistry()
    handle = await registry.start("run-skipped")
    workflow = AgentWorkflow()

    result = await workflow.run(
        message="帮我看下这周我的收益有多少",
        conversation_id="c1",
        user_token="token",
        system="rental",
        context={},
        event_emitter=handle.emitter,
        cancellation=handle.cancellation,
    )

    assert result["toolCalls"][0]["status"] == "skipped"
    assert "未配置网关地址" in result["toolCalls"][0]["reason"]
    assert result["workspace"]["summary"]["status"] == "needs_data"
    assert result["workspace"]["alerts"][0]["level"] == "error"
    events = []
    while not handle.queue.empty():
        events.append((await handle.queue.get()).type)
    assert "tool.completed" in events
    assert "tool.failed" not in events


@pytest.mark.asyncio
async def test_workflow_uses_llm_generated_questions_for_vague_trend():
    model = FakeClarificationModel()
    workflow = AgentWorkflow(
        model_config_client=FakeClarificationConfigClient(),
        llm_factory=lambda config: model,
        sql_clients={"alipay": object()},
    )

    result = await workflow.run(
        message="近 30 天趋势",
        conversation_id="c1",
        user_token="token",
        system="alipay",
        context={},
    )

    assert model.messages
    assert result["toolCalls"] == []
    assert result["charts"] == []
    assert result["questions"][0]["question"] == "你想看哪个趋势？"
    assert result["questions"][0]["options"][0]["label"] == "订单数趋势"
    assert result["questions"][0]["options"][0]["value"] == "查看近 30 天订单数趋势"
    assert result["answer"] == "结论：需要先确认你要看的趋势指标。"


@pytest.mark.asyncio
async def test_workflow_plans_supplemental_alipay_readonly_tools():
    gateway = FakeGateway()
    docker_logs = FakeDockerLogsClient()
    workflow = AgentWorkflow(
        gateway_base_url="http://gateway",
        gateway_factory=lambda base_url, token: gateway,
        docker_logs_client=docker_logs,
    )

    result = await workflow.run(
        message="查一下支付宝订单 20260708123456 的押金扣款和商品同步日志，看看服务报错",
        conversation_id="c1",
        user_token="token",
        system="alipay",
        context={},
    )

    names = [item["name"] for item in result["toolCalls"]]
    assert "query_alipay_order_oper_logs" in names
    assert "query_alipay_goods_sync_logs" in names
    assert "query_alipay_deposit" in names
    assert "query_alipay_deposit_deduct_records" in names
    assert "query_alipay_docker_logs" in names
    assert docker_logs.requests
    docker_call = next(item for item in result["toolCalls"] if item["name"] == "query_alipay_docker_logs")
    assert docker_call["status"] == "success"
    assert "keyword" not in docker_call
    assert "samples" not in docker_call
    assert any(request[1] == "/api/web/goods/sync-logs/page" for request in gateway.requests)
    assert any(action["title"] == "准备同步重试前检查" for action in result["workspace"]["actionDrafts"])


@pytest.mark.asyncio
async def test_workflow_uses_web_search_for_online_information_question():
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(web_search_client=search_client)

    result = await workflow.run(
        message="联网搜索一下支付宝租赁交易组件最新接入规则",
        conversation_id="c1",
        user_token="token",
        system="alipay",
        context={},
    )

    assert search_client.requests
    assert result["toolCalls"][0]["name"] == "search_web"
    assert result["toolCalls"][0]["status"] == "success"
    assert result["answer"].startswith("结论：")
    assert "来源：" in result["answer"]
    assert "摘要：" not in result["answer"]
    assert "后续建议" not in result["answer"]
    assert "opendocs.alipay.com" in result["answer"]
    assert "支付宝租赁交易组件接入文档" in result["answer"]


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "message",
    ["现在的时间是多久", "现在几点", "你联网查询，现在的时间"],
)
async def test_current_time_questions_use_structured_mcp_time_instead_of_web_search(message):
    client = FakeCurrentTimeClient()
    workflow = AgentWorkflow(web_search_client=client)

    result = await workflow.run(
        message=message,
        conversation_id="c-current-time",
        user_token=None,
        system="alipay",
        context={},
    )

    assert client.time_requests == ["Asia/Shanghai"]
    assert client.requests == []
    assert result["toolCalls"][0]["name"] == "current_time"
    assert result["toolCalls"][0]["status"] == "success"
    assert "2026年07月10日 21:15:30" in result["answer"]
    assert "2025" not in result["answer"]
    assert result["sources"] == []
    assert len(result["workspace"]["nextPrompts"]) == 2


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("message", "expected_timezone", "expected_time"),
    [
        ("查询当前 UTC 时间", "UTC", "13:15:30"),
        ("查询当前纽约当地时间", "America/New_York", "09:15:30"),
        ("纽约现在几点", "America/New_York", "09:15:30"),
    ],
)
async def test_current_time_questions_select_the_requested_timezone(message, expected_timezone, expected_time):
    client = FakeCurrentTimeClient()
    workflow = AgentWorkflow(web_search_client=client)

    result = await workflow.run(
        message=message,
        conversation_id="c-timezone",
        user_token=None,
        system="alipay",
        context={},
    )

    assert client.time_requests == [expected_timezone]
    assert expected_time in result["answer"]
    assert result["toolCalls"][0]["name"] == "current_time"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "message,context",
    [
        (
            "今天几号",
            {"conversationHistory": [{"role": "user", "content": "查询支付宝订单状态"}]},
        ),
        (
            "你联网查询，现在的时间",
            {"conversationContext": {"topic": "天气", "pendingQuestion": "你想查询哪个城市的天气？"}},
        ),
    ],
)
async def test_current_time_intent_overrides_stale_business_or_weather_context(message, context):
    client = FakeCurrentTimeClient()
    workflow = AgentWorkflow(web_search_client=client)

    result = await workflow.run(
        message=message,
        conversation_id="c-time-context",
        user_token=None,
        system="alipay",
        context=context,
    )

    assert client.time_requests == ["Asia/Shanghai"]
    assert result["questions"] == []
    assert result["toolCalls"][0]["name"] == "current_time"
    assert "2026年07月10日 21:15:30" in result["answer"]


@pytest.mark.asyncio
async def test_workflow_asks_for_topic_when_web_search_request_is_vague():
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(web_search_client=search_client)

    result = await workflow.run(
        message="联网查一下，然后总结给我",
        conversation_id="c1",
        user_token="token",
        system="alipay",
        context={},
    )

    assert search_client.requests == []
    assert result["toolCalls"][0]["name"] == "search_web"
    assert result["toolCalls"][0]["status"] == "skipped"
    assert "要查什么主题" in result["answer"]


@pytest.mark.asyncio
async def test_workflow_resolves_vague_web_search_from_conversation_history():
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(web_search_client=search_client)

    result = await workflow.run(
        message="网上搜索，我说得是",
        conversation_id="c1",
        user_token="token",
        system="alipay",
        context={
            "conversationHistory": [
                {"role": "user", "content": "我想知道这个月有多少订单"},
                {"role": "assistant", "content": "本月订单数 58"},
                {"role": "user", "content": "去年呢"},
                {"role": "assistant", "content": "去年订单数 960"},
            ]
        },
    )

    assert search_client.requests
    query, _, domains = search_client.requests[0]
    assert "支付宝租赁" in query
    assert "订单" in query
    assert "我说" not in query
    assert domains
    assert result["toolCalls"][0]["status"] == "success"
    assert "opendocs.alipay.com" in result["answer"]


@pytest.mark.asyncio
async def test_workflow_general_chat_does_not_call_business_tools():
    gateway = FakeGateway()
    workflow = AgentWorkflow(
        gateway_base_url="http://gateway",
        gateway_factory=lambda base_url, token: gateway,
    )

    result = await workflow.run(
        message="你好，你可以陪我聊聊天吗",
        conversation_id="c1",
        user_token="token",
        system="alipay",
        context={},
    )

    assert gateway.requests == []
    assert result["toolCalls"] == []
    assert result["workspace"]["mode"] == "chat"
    assert "可以" in result["answer"]


@pytest.mark.asyncio
async def test_long_general_chat_does_not_fall_into_business_diagnosis():
    gateway = FakeGateway()
    workflow = AgentWorkflow(
        gateway_base_url="http://gateway",
        gateway_factory=lambda base_url, token: gateway,
    )

    result = await workflow.run(
        message="请帮我把下面这段说明改得更自然一些，语气友好但不要太正式",
        conversation_id="c-long-chat",
        user_token=None,
        system="secondhand",
        context={},
    )

    assert gateway.requests == []
    assert result["toolCalls"] == []
    assert result["workspace"]["mode"] == "chat"


@pytest.mark.asyncio
async def test_weather_without_city_returns_question_without_tools():
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(web_search_client=search_client)

    result = await workflow.run(
        message="今天天气怎么样",
        conversation_id="c-weather-vague",
        user_token=None,
        system="alipay",
        context={},
    )

    assert result["toolCalls"] == []
    assert search_client.requests == []
    assert result["questions"][0]["question"] == "你想查询哪个城市的天气？"
    assert result["contextSnapshot"]["topic"] == "天气"


@pytest.mark.asyncio
async def test_temperature_without_city_returns_weather_question_without_tools():
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(web_search_client=search_client)

    result = await workflow.run(
        message="今天多少度呀",
        conversation_id="c-temperature-vague",
        user_token=None,
        system="alipay",
        context={},
    )

    assert result["toolCalls"] == []
    assert search_client.requests == []
    assert result["questions"][0]["question"] == "你想查询哪个城市的天气？"
    assert result["contextSnapshot"]["topic"] == "天气"


@pytest.mark.asyncio
async def test_bare_city_answer_to_weather_question_searches_web():
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(web_search_client=search_client)

    result = await workflow.run(
        message="成都",
        conversation_id="c-temperature-city",
        user_token=None,
        system="alipay",
        context={
            "conversationContext": {
                "topic": "天气",
                "timeRange": "今天",
                "pendingQuestion": "你想查询哪个城市的天气？",
            }
        },
    )

    assert result["questions"] == []
    assert result["toolCalls"][0]["name"] == "search_web"
    assert "成都" in search_client.requests[0][0]
    assert "今天" in search_client.requests[0][0]


@pytest.mark.asyncio
async def test_weather_followup_uses_confirmed_city_and_searches_web():
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(web_search_client=search_client)

    result = await workflow.run(
        message="那明天呢",
        conversation_id="c-weather-followup",
        user_token=None,
        system="alipay",
        context={"conversationContext": {"topic": "天气", "location": "杭州", "timeRange": "今天"}},
    )

    assert result["questions"] == []
    assert result["toolCalls"][0]["name"] == "search_web"
    assert "杭州" in search_client.requests[0][0]
    assert "明天" in search_client.requests[0][0]
    assert result["sources"][0]["title"] == "支付宝租赁交易组件接入文档"
    assert result["sources"][0]["rank"] == 1


@pytest.mark.asyncio
async def test_current_news_question_auto_searches_web():
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(web_search_client=search_client)

    result = await workflow.run(
        message="今天有什么科技新闻",
        conversation_id="c-news",
        user_token=None,
        system="rental",
        context={},
    )

    assert search_client.requests
    assert result["toolCalls"][0]["name"] == "search_web"
    assert result["sources"]


@pytest.mark.asyncio
async def test_bare_lookup_requires_clarification_before_tools():
    workflow = AgentWorkflow()

    result = await workflow.run(
        message="查一下",
        conversation_id="c-vague",
        user_token=None,
        system="secondhand",
        context={},
    )

    assert result["toolCalls"] == []
    assert result["questions"][0]["question"] == "你想查询什么内容？"


def test_final_result_only_answer_removes_process_sections():
    answer = final_result_only_answer(
        "结论：近30天暂时没有可用趋势数据。\n\n"
        "证据：内部工具没有返回 rows。\n\n"
        "后续建议：需要先确认只读数据库连接。"
    )

    assert answer == "结论：近30天暂时没有可用趋势数据。"
    assert "证据" not in answer
    assert "后续建议" not in answer
    assert "需要先" not in answer


def test_final_result_only_answer_localizes_visible_internal_field_names():
    answer = final_result_only_answer(
        "结论：按今天看，total_sales 16300，total_cost 14955，total_profit 1345，"
        "metric_value0，createdAt 1783526438653，status1。"
    )

    assert "销售额" in answer
    assert "成本" in answer
    assert "利润" in answer
    assert "指标值0" in answer
    assert "创建时间" in answer
    assert "状态1" in answer
    assert "total_sales" not in answer
    assert "total_cost" not in answer
    assert "total_profit" not in answer
    assert "metric_value" not in answer
    assert "createdAt" not in answer
    assert "status" not in answer


@pytest.mark.asyncio
async def test_workflow_general_chat_answers_simple_arithmetic_without_llm():
    workflow = AgentWorkflow()

    result = await workflow.run(
        message="我想知道1+1是几",
        conversation_id="c1",
        user_token="token",
        system="alipay",
        context={},
    )

    assert result["toolCalls"] == []
    assert "1 + 1 = 2" in result["answer"]

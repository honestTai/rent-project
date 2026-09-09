from __future__ import annotations

import pytest

from equipment_agent.agent.workflow import AgentWorkflow
from equipment_agent.config.model_config import ModelConfig


class FakeConfigClient:
    async def load(self) -> ModelConfig:
        return ModelConfig(
            provider="ds",
            model="deepseek-chat",
            base_url="https://api.deepseek.com",
            api_key="test-key",
        )


class FakeResponse:
    content = "结论：模型已按只读策略生成回答。"


class TextResponse:
    def __init__(self, content: str) -> None:
        self.content = content


class FakeModel:
    def __init__(self) -> None:
        self.messages = []

    async def ainvoke(self, messages):
        self.messages = messages
        return FakeResponse()


@pytest.mark.asyncio
async def test_workflow_uses_llm_when_model_config_has_api_key():
    model = FakeModel()
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
    )

    result = await workflow.run(
        message="查一下支付宝订单问题",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    assert result["answer"] == "结论：模型已按只读策略生成回答。"
    assert any("只读" in item.content for item in model.messages)


@pytest.mark.asyncio
async def test_workflow_falls_back_when_llm_is_not_configured():
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: None,
    )

    result = await workflow.run(
        message="查一下支付宝订单问题",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    assert "只读" in result["answer"]


@pytest.mark.asyncio
async def test_general_chat_uses_natural_model_answer_without_business_tools():
    model = SequencedAgentLoopModel([
        TextResponse("当然可以。把原句发给我，我会帮你改得更自然。"),
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
    )

    result = await workflow.run(
        message="帮我润色一句话",
        conversation_id="c-chat",
        user_token=None,
        system="alipay",
        context={},
    )

    assert result["toolCalls"] == []
    assert "把原句发给我" in result["answer"]


@pytest.mark.asyncio
async def test_planner_prompt_receives_structured_conversation_context():
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([{
            "name": "search_web",
            "args": {"query": "杭州明天天气"},
            "id": "call_weather",
        }]),
        TextResponse('{"action":"final"}'),
        TextResponse("结论：已查询杭州明天天气。"),
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        web_search_client=FakeWebSearchClient(),
    )

    await workflow.run(
        message="那明天呢",
        conversation_id="c-context-prompt",
        user_token=None,
        system="alipay",
        context={"conversationContext": {"topic": "天气", "location": "杭州", "timeRange": "今天"}},
    )

    prompt = "\n".join(
        str(getattr(item, "content", ""))
        for item in model.all_messages[0]
    )
    assert "当前主题：天气" in prompt
    assert "城市/地区：杭州" in prompt
    assert "时间范围：明天" in prompt


class FakeToolCallResponse:
    content = ""

    def __init__(self, tool_calls):
        self.tool_calls = tool_calls


class FakeToolCallingModel(FakeModel):
    def __init__(self) -> None:
        super().__init__()
        self.bound_tools = []
        self.calls = 0
        self.all_messages = []

    def bind_tools(self, tools, **kwargs):
        self.bound_tools = tools
        return self

    async def ainvoke(self, messages):
        self.calls += 1
        self.messages = messages
        self.all_messages.append(messages)
        if self.calls == 1:
            return FakeToolCallResponse([
                {
                    "name": "search_web",
                    "args": {"query": "支付宝租赁 2026年7月 订单量 统计"},
                    "id": "call_1",
                }
            ])
        return FakeResponse()


class FixedToolCallingModel(FakeModel):
    def __init__(self, tool_call) -> None:
        super().__init__()
        self.tool_call = tool_call
        self.bound_tools = []

    def bind_tools(self, tools, **kwargs):
        self.bound_tools = tools
        return self

    async def ainvoke(self, messages):
        self.messages = messages
        return FakeToolCallResponse([self.tool_call])


class SequencedAgentLoopModel(FakeModel):
    def __init__(self, responses) -> None:
        super().__init__()
        self.responses = list(responses)
        self.bound_tools = []
        self.all_messages = []

    def bind_tools(self, tools, **kwargs):
        self.bound_tools = tools
        return self

    async def ainvoke(self, messages):
        self.all_messages.append(messages)
        self.messages = messages
        if not self.responses:
            return FakeResponse()
        response = self.responses.pop(0)
        return response() if isinstance(response, type) else response


class FakeSqlClientForLoop:
    def __init__(self) -> None:
        self.queries = []

    def list_table_names(self):
        return ["rent_order", "order_operation_log"]

    def describe_tables(self, table_names):
        return {
            "tables": [
                {
                    "name": "rent_order",
                    "exists": True,
                    "columns": [
                        {"name": "order_id", "type": "bigint", "nullable": False},
                        {"name": "created_at", "type": "bigint", "nullable": True},
                        {"name": "paid_amount", "type": "int", "nullable": True},
                    ],
                }
                for _ in [1]
            ]
        }

    def query(self, sql, params=None, max_rows=None):
        self.queries.append((sql, params or {}, max_rows))
        return {
            "rows": [{"date": "2026-07-09", "paidAmountYuan": 12.0, "orderCount": 2}],
            "rowCount": 1,
            "truncated": False,
        }


class FakeSecondhandRevenueSqlClientForLoop:
    def __init__(self) -> None:
        self.queries = []

    def list_table_names(self):
        return ["second_device_out"]

    def describe_tables(self, table_names):
        return {
            "tables": [
                {
                    "name": "second_device_out",
                    "exists": True,
                    "columns": [
                        {"name": "buyDate", "type": "date", "nullable": True},
                        {"name": "buyMoney", "type": "decimal", "nullable": True},
                        {"name": "costPrice", "type": "decimal", "nullable": True},
                    ],
                }
            ]
        }

    def query(self, sql, params=None, max_rows=None):
        self.queries.append((sql, params or {}, max_rows))
        return {
            "rows": [{"date": "2026-07-09", "revenue": 16300.0, "profit": 1345.0}],
            "rowCount": 1,
            "truncated": False,
        }


class FakeDockerLogsClientForLoop:
    def __init__(self) -> None:
        self.requests = []

    def query(self, system, keyword="", *, tail=None, since=None):
        self.requests.append((system, keyword, tail, since))
        return {
            "available": True,
            "summary": "已读取 1 个容器的最近日志，未发现新异常。",
            "containers": [],
        }


class FakeWebSearchClient:
    def __init__(self) -> None:
        self.requests = []

    async def search(self, query, *, limit=5, domains=None):
        self.requests.append((query, limit, domains))
        return [
            {
                "title": "支付宝租赁公开资料",
                "url": "https://opendocs.alipay.com/open/demo",
                "snippet": "支付宝租赁订单相关公开规则。",
                "source": "opendocs.alipay.com",
            }
        ]


class EmptyKnowledgeStore:
    def __init__(self) -> None:
        self.requests = []

    def search(self, query, limit=5, system=None):
        self.requests.append((query, limit, system))
        return []


@pytest.mark.asyncio
async def test_realtime_weather_cannot_be_downgraded_to_general_chat_by_llm():
    model = FixedToolCallingModel({
        "name": "general_chat",
        "args": {},
        "id": "call_wrong_chat_route",
    })
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        web_search_client=search_client,
    )

    result = await workflow.run(
        message="今天杭州天气怎么样",
        conversation_id="c-weather-route-guard",
        user_token=None,
        system="alipay",
        context={},
    )

    assert search_client.requests
    assert result["toolCalls"][0]["name"] == "search_web"
    assert result["toolCalls"][0]["status"] == "success"
    assert result["sources"][0]["url"].startswith("https://")


@pytest.mark.asyncio
async def test_web_mcp_evidence_is_summarized_by_llm_and_returns_counter_question():
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([{
            "name": "search_web",
            "args": {"query": "支付宝租赁交易组件最新接入规则"},
            "id": "call_web_mcp",
        }]),
        TextResponse('{"action":"final"}'),
        TextResponse(
            """
            {
              "answer": "官方资料显示，接入前需要先核对商户资格与当前开放范围，具体以文档中的实时规则为准：https://opendocs.alipay.com/open/demo",
              "followUpQuestion": "你想继续核对商户适用条件，还是整理成接入检查清单？",
              "followUpPrompts": [
                {"label": "核对适用条件", "prompt": "继续联网核对支付宝租赁交易组件的商户适用条件"},
                {"label": "整理检查清单", "prompt": "把支付宝租赁交易组件最新规则整理成接入检查清单"}
              ]
            }
            """
        ),
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        web_search_client=FakeWebSearchClient(),
    )

    result = await workflow.run(
        message="联网查一下支付宝租赁交易组件最新接入规则",
        conversation_id="c-web-mcp-summary",
        user_token=None,
        system="alipay",
        context={},
    )

    assert "商户资格" in result["answer"]
    assert "你想继续核对商户适用条件" in result["answer"]
    assert result["questions"] == []
    assert result["workspace"]["nextPrompts"][0]["label"] == "核对适用条件"
    final_prompt = "\n".join(str(getattr(item, "content", "")) for item in model.all_messages[-1])
    assert "支付宝租赁订单相关公开规则" in final_prompt
    assert "外部不可信数据" in final_prompt


@pytest.mark.asyncio
async def test_sql_planner_prefers_llm_generated_query_and_keeps_bound_params():
    model = SequencedAgentLoopModel([
        TextResponse(
            """
            {
              "sql": "SELECT DATE(FROM_UNIXTIME(created_at / 1000)) AS dayKey, COUNT(1) AS totalOrders FROM rent_order WHERE created_at >= :startMs AND created_at < :endMs GROUP BY dayKey ORDER BY dayKey ASC",
              "params": {"startMs": 1783612800000, "endMs": 1784217600000},
              "title": "模型生成的订单走势",
              "metricLabel": "每日订单数",
              "xKey": "dayKey",
              "valueKeys": ["totalOrders"],
              "chartType": "line",
              "answerFocus": "根据真实表结构按天汇总"
            }
            """
        )
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
    )
    state = {
        "intent": "sql_analysis",
        "system": "alipay",
        "message": "分析最近7天支付宝租赁订单趋势并画图",
        "original_message": "分析最近7天支付宝租赁订单趋势并画图",
        "analysis_meta": {
            "schemaTool": "inspect_alipay_business_schema",
            "planTool": "plan_alipay_readonly_sql",
            "startDate": "2026-07-10",
            "endDate": "2026-07-16",
            "rangeText": "最近 7 天",
        },
        "tool_results": {
            "inspect_alipay_business_schema": {
                "tables": [{
                    "name": "rent_order",
                    "exists": True,
                    "columns": [
                        {"name": "created_at", "type": "bigint"},
                        {"name": "paid_amount", "type": "int"},
                    ],
                }],
            },
        },
    }
    call = {"name": "plan_alipay_readonly_sql", "status": "planned"}

    await workflow._execute_sql_plan_tool(state, call)

    plan = state["tool_results"]["plan_alipay_readonly_sql"]
    assert call["status"] == "success"
    assert plan["title"] == "模型生成的订单走势"
    assert plan["xKey"] == "dayKey"
    assert plan["params"] == {"startMs": 1783612800000, "endMs": 1784217600000}


@pytest.mark.asyncio
async def test_workflow_lets_llm_select_web_search_tool():
    model = FakeToolCallingModel()
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        web_search_client=search_client,
    )

    result = await workflow.run(
        message="网上搜索，我说得是",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={
            "conversationHistory": [
                {"role": "user", "content": "我想知道这个月有多少订单"},
                {"role": "assistant", "content": "本月订单数 58"},
            ]
        },
    )

    assert model.bound_tools
    assert search_client.requests
    query, _, _ = search_client.requests[0]
    assert "支付宝租赁" in query
    assert "订单" in query
    assert "规则" in query
    assert "2026" not in query
    assert "2026年7月" not in query
    assert "订单量" not in query
    assert result["toolCalls"][0]["name"] == "search_web"
    assert result["toolCalls"][0]["status"] == "success"


@pytest.mark.asyncio
async def test_llm_planner_receives_project_business_knowledge():
    model = FakeToolCallingModel()
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        web_search_client=FakeWebSearchClient(),
    )

    await workflow.run(
        message="我想知道我6月份的销售情况",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    prompt = "\n".join(
        str(getattr(message, "content", ""))
        for messages in model.all_messages
        for message in messages
    )
    assert "无人机设备管理系统业务 Skill" in prompt
    assert "rent_order.created_at 是毫秒时间戳" in prompt
    assert "paid_amount / 100" in prompt
    assert "second_douyin_order" in prompt


@pytest.mark.asyncio
async def test_llm_can_route_diagnosis_to_business_system_different_from_page():
    model = FixedToolCallingModel({
        "name": "diagnose_business",
        "args": {"question": "查抖音订单同步失败日志", "target_system": "secondhand"},
        "id": "call_1",
    })
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
    )

    result = await workflow.run(
        message="查抖音订单同步失败日志",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    assert result["system"] == "secondhand"
    assert result["toolCalls"][0]["system"] == "secondhand"
    assert result["toolCalls"][0]["name"] == "query_secondhand_operation_logs"
    assert any(call["name"] == "query_secondhand_douyin_api_logs" for call in result["toolCalls"])


@pytest.mark.asyncio
async def test_llm_sql_tool_can_target_rental_from_alipay_page():
    model = FixedToolCallingModel({
        "name": "query_readonly_sql",
        "args": {"question": "查设备租赁本月订单数", "target_system": "rental"},
        "id": "call_1",
    })
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
    )

    result = await workflow.run(
        message="查设备租赁本月订单数",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    assert result["system"] == "rental"
    assert [call["name"] for call in result["toolCalls"][:3]] == [
        "inspect_rental_business_schema",
        "plan_rental_readonly_sql",
        "execute_rental_readonly_sql",
    ]
    assert all(call["system"] == "rental" for call in result["toolCalls"][:3])


@pytest.mark.asyncio
async def test_agent_loop_lets_llm_clarify_before_tools():
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([
            {
                "name": "ask_clarification",
                "args": {
                    "question": "你想查哪个指标？",
                    "options": [
                        {"label": "订单数", "value": "查看近 30 天订单数趋势"},
                        {"label": "销售金额", "value": "查看近 30 天销售金额趋势"},
                    ],
                },
                "id": "call_1",
            }
        ])
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        sql_clients={"alipay": FakeSqlClientForLoop()},
    )

    result = await workflow.run(
        message="看下最近趋势",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    assert model.bound_tools
    assert result["toolCalls"] == []
    assert result["questions"][0]["question"] == "你想查哪个指标？"
    assert result["questions"][0]["options"][0]["value"] == "查看近 30 天订单数趋势"
    assert result["answer"] == "结论：需要先确认你要看的指标。"


@pytest.mark.asyncio
async def test_agent_loop_can_reflect_and_call_second_tool_before_final_answer():
    sql_client = FakeSqlClientForLoop()
    docker_logs = FakeDockerLogsClientForLoop()
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([
            {
                "name": "query_readonly_sql",
                "args": {"question": "查支付宝今天销售金额", "target_system": "alipay"},
                "id": "call_1",
            }
        ]),
        TextResponse('{"action":"tool","tool":"query_docker_logs","question":"检查支付宝服务今天销售相关异常","target_system":"alipay"}'),
        TextResponse('{"action":"final"}'),
        TextResponse("结论：今天销售金额为 12.00 元，订单数 2 单，服务日志未发现新异常。"),
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        sql_clients={"alipay": sql_client},
        docker_logs_client=docker_logs,
    )

    result = await workflow.run(
        message="今天销售怎么样，顺便看下服务有没有异常",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    assert sql_client.queries
    assert docker_logs.requests
    names = [call["name"] for call in result["toolCalls"]]
    assert "execute_alipay_readonly_sql" in names
    assert "query_alipay_docker_logs" in names
    assert "12.00 元" in result["answer"]
    assert "订单数 2" in result["answer"]
    assert "未发现新异常" in result["answer"]
    assert "query_alipay_docker_logs" not in result["answer"]


@pytest.mark.asyncio
async def test_agent_loop_guardrail_adds_log_tool_when_compound_request_mentions_service_exception():
    sql_client = FakeSecondhandRevenueSqlClientForLoop()
    docker_logs = FakeDockerLogsClientForLoop()
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([
            {
                "name": "query_readonly_sql",
                "args": {"question": "查二手交易今天销售收益", "target_system": "secondhand"},
                "id": "call_1",
            }
        ]),
        TextResponse(
            """
            {
              "sql": "SELECT buyDate AS date, SUM(buyMoney) AS revenue, SUM(buyMoney - costPrice) AS profit FROM second_device_out WHERE buyDate >= :startDate AND buyDate < :endDate GROUP BY buyDate ORDER BY buyDate ASC",
              "title": "今日二手交易收益",
              "metricLabel": "收益",
              "xKey": "date",
              "valueKeys": ["revenue", "profit"],
              "chartType": "bar",
              "answerFocus": "按销售额和利润回答"
            }
            """
        ),
        TextResponse('{"action":"final"}'),
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        sql_clients={"secondhand": sql_client},
        docker_logs_client=docker_logs,
    )

    result = await workflow.run(
        message="今天销售怎么样，顺便看服务有没有异常",
        conversation_id="c1",
        user_token=None,
        system="secondhand",
        context={},
    )

    names = [call["name"] for call in result["toolCalls"]]
    assert "execute_secondhand_readonly_sql" in names
    assert "query_secondhand_docker_logs" in names
    assert docker_logs.requests
    assert "销售额" in result["answer"]
    assert "利润" in result["answer"]
    assert "revenue" not in result["answer"]
    assert "profit" not in result["answer"]


@pytest.mark.asyncio
async def test_agent_loop_preserves_original_sales_intent_when_llm_rewrites_question_badly():
    sql_client = FakeSqlClientForLoop()
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([
            {
                "name": "query_readonly_sql",
                "args": {"question": "查今天订单支付状态", "target_system": "alipay"},
                "id": "call_1",
            }
        ]),
        TextResponse('{"action":"final"}'),
        TextResponse("结论：今天销售金额为 12.00 元，订单数 2 单。"),
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        sql_clients={"alipay": sql_client},
    )

    result = await workflow.run(
        message="今天销售怎么样",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    sql = sql_client.queries[0][0]
    assert "paid_amount" in sql
    assert "orderStatus" not in sql
    assert result["charts"][0]["title"] == "支付宝租赁销售情况"


@pytest.mark.asyncio
async def test_agent_loop_does_not_replace_valid_chart_with_unneeded_followup_sql():
    class DeviceSqlClient(FakeSqlClientForLoop):
        def query(self, sql, params=None, max_rows=None):
            self.queries.append((sql, params or {}, max_rows))
            return {
                "rows": [{"deviceName": "A", "orderCount": 5, "revenueYuan": 80.0, "avgRevenueYuan": 16.0}],
                "rowCount": 1,
                "truncated": False,
            }

    sql_client = DeviceSqlClient()
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([
            {
                "name": "query_readonly_sql",
                "args": {"question": "查哪些设备租得好", "target_system": "alipay"},
                "id": "call_1",
            }
        ]),
        TextResponse(
            """
            {
              "sql": "SELECT goods_title AS deviceName, COUNT(1) AS orderCount, SUM(paid_amount) / 100 AS revenueYuan FROM rent_order GROUP BY goods_title ORDER BY revenueYuan DESC LIMIT 10",
              "params": {},
              "title": "支付宝租赁设备表现排行",
              "metricLabel": "设备收入与订单数",
              "xKey": "deviceName",
              "valueKeys": ["revenueYuan", "orderCount"],
              "chartType": "bar",
              "answerFocus": "优先分析收入和订单数领先的设备"
            }
            """
        ),
        TextResponse('{"action":"tool","tool":"query_readonly_sql","question":"查订单支付状态","target_system":"alipay"}'),
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        sql_clients={"alipay": sql_client},
    )

    result = await workflow.run(
        message="我是否需要开启营销呢，或者哪些设备租的好",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    assert len(sql_client.queries) == 1
    assert result["charts"][0]["title"] == "支付宝租赁设备表现排行"
    assert "优先围绕「A」开启营销" in result["answer"]


@pytest.mark.asyncio
async def test_agent_loop_treats_goods_order_and_sales_ranking_as_device_performance():
    class DeviceSqlClient(FakeSqlClientForLoop):
        def query(self, sql, params=None, max_rows=None):
            self.queries.append((sql, params or {}, max_rows))
            return {
                "rows": [{"deviceName": "商品A", "orderCount": 8, "revenueYuan": 120.0, "avgRevenueYuan": 15.0}],
                "rowCount": 1,
                "truncated": False,
            }

    sql_client = DeviceSqlClient()
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([
            {
                "name": "query_readonly_sql",
                "args": {"question": "查最近30天各商品租赁订单量和销售额", "target_system": "alipay"},
                "id": "call_1",
            }
        ]),
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        sql_clients={"alipay": sql_client},
    )

    result = await workflow.run(
        message="哪些设备租的好",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    sql = sql_client.queries[0][0]
    assert "goods_title" in sql
    assert "订单支付状态" not in result["answer"]
    assert result["charts"][0]["title"] == "支付宝租赁设备表现排行"


class MultiToolPlanningModel(FakeModel):
    def __init__(self) -> None:
        super().__init__()
        self.bound_tools = []
        self.calls = 0

    def bind_tools(self, tools, **kwargs):
        self.bound_tools = tools
        return self

    async def ainvoke(self, messages):
        self.calls += 1
        self.messages = messages
        if self.calls == 1:
            return FakeToolCallResponse([
                {
                    "name": "query_docker_logs",
                    "args": {
                        "question": "检查支付宝服务最近异常",
                        "target_system": "alipay",
                    },
                    "id": "call_logs",
                },
                {
                    "name": "search_web",
                    "args": {"query": "支付宝租赁交易组件 官方异常排查文档"},
                    "id": "call_docs",
                },
            ])
        if self.calls == 2:
            return TextResponse('{"action":"final"}')
        return TextResponse("结论：已同时检查服务日志和支付宝官方排查资料。")


@pytest.mark.asyncio
async def test_llm_planner_executes_every_safe_tool_call_in_one_plan():
    model = MultiToolPlanningModel()
    search_client = FakeWebSearchClient()
    docker_logs = FakeDockerLogsClientForLoop()
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        web_search_client=search_client,
        docker_logs_client=docker_logs,
    )

    result = await workflow.run(
        message="查支付宝服务异常，并搜索官方排查文档",
        conversation_id="c-multi",
        user_token=None,
        system="alipay",
        context={},
    )

    assert docker_logs.requests
    assert search_client.requests
    names = [call["name"] for call in result["toolCalls"]]
    assert "query_alipay_docker_logs" in names
    assert "search_web" in names
    assert all(call["status"] == "success" for call in result["toolCalls"])


@pytest.mark.asyncio
async def test_llm_can_continue_with_web_search_after_a_chart_is_available():
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([
            {
                "name": "fetch_secondhand_analysis_dashboard",
                "args": {"question": "查看二手交易趋势"},
                "id": "call_dashboard",
            }
        ]),
        TextResponse('{"action":"tool","tool":"search_web","args":{"query":"二手设备交易市场最新趋势"}}'),
        TextResponse('{"action":"final"}'),
        TextResponse("结论：已结合内部趋势和公开市场资料完成汇总。"),
    ])
    gateway = type("Gateway", (), {
        "requests": [],
        "request": lambda self, method, path, payload: _async_value({
            "code": 0,
            "data": {
                "summary": {"orderCount": 2, "salesAmount": 1000},
                "trend": [{"dateLabel": "2026-07-10", "orderCount": 2, "salesAmount": 1000, "profitAmount": 120}],
            },
        }),
    })()
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        gateway_base_url="http://gateway",
        gateway_factory=lambda base_url, token: gateway,
        web_search_client=search_client,
    )

    result = await workflow.run(
        message="分析二手交易趋势，并结合最新公开市场信息",
        conversation_id="c-chart-search",
        user_token=None,
        system="secondhand",
        context={},
    )

    assert result["charts"]
    assert search_client.requests
    assert "search_web" in [call["name"] for call in result["toolCalls"]]


@pytest.mark.asyncio
async def test_llm_does_not_execute_the_same_tool_signature_twice():
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([
            {"name": "search_web", "args": {"query": "杭州天气"}, "id": "call_1"}
        ]),
        TextResponse('{"action":"tool","tool":"search_web","args":{"query":"杭州天气"}}'),
        TextResponse("结论：已取得杭州天气公开资料。"),
    ])
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        web_search_client=search_client,
    )

    await workflow.run(
        message="联网搜索杭州天气",
        conversation_id="c-duplicate",
        user_token=None,
        system="alipay",
        context={},
    )

    assert len(search_client.requests) == 1


@pytest.mark.asyncio
async def test_llm_can_fall_back_to_web_after_empty_local_knowledge():
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([
            {"name": "search_secondhand_skill", "args": {"question": "二手无人机保值规则"}, "id": "call_knowledge"}
        ]),
        TextResponse('{"action":"tool","tool":"search_web","args":{"query":"二手无人机保值规则"}}'),
        TextResponse('{"action":"final"}'),
        TextResponse("结论：本地知识未命中，已根据公开资料汇总。"),
    ])
    knowledge_store = EmptyKnowledgeStore()
    search_client = FakeWebSearchClient()
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        knowledge_store=knowledge_store,
        web_search_client=search_client,
    )

    result = await workflow.run(
        message="二手无人机保值规则是什么",
        conversation_id="c-knowledge-fallback",
        user_token=None,
        system="secondhand",
        context={},
    )

    assert knowledge_store.requests
    assert search_client.requests
    assert result["sources"]


async def _async_value(value):
    return value


@pytest.mark.asyncio
async def test_planner_exposes_and_executes_specific_business_tools_without_keyword_routing():
    class DirectToolGateway:
        def __init__(self) -> None:
            self.requests = []

        async def request(self, method, path, payload):
            self.requests.append((method, path, payload))
            return {"code": 0, "data": {"riskLevel": "HIGH", "reason": "履约信息待核验"}}

    gateway = DirectToolGateway()
    model = SequencedAgentLoopModel([
        FakeToolCallResponse([
            {
                "name": "query_alipay_risk_detail",
                "args": {"question": "检查当前订单的支付宝风控详情"},
                "id": "call_risk",
            }
        ]),
        TextResponse('{"action":"final"}'),
        TextResponse("结论：当前订单存在高风险信号，需要人工核验履约信息。"),
    ])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        gateway_base_url="http://gateway",
        gateway_factory=lambda base_url, token: gateway,
    )

    result = await workflow.run(
        message="帮我深挖一下这个问题",
        conversation_id="c-direct-tool",
        user_token="token",
        system="alipay",
        context={"conversationHistory": [{"role": "user", "content": "刚才那个支付宝订单风险很高"}]},
    )

    bound_names = {
        spec["function"]["name"]
        for spec in model.bound_tools
        if spec.get("type") == "function"
    }
    assert "query_alipay_risk_detail" in bound_names
    assert gateway.requests[0][1] == "/api/web/rent-component/risk-detail"
    assert result["toolCalls"][0]["displayName"] == "查询支付宝风控详情"


def test_planner_only_exposes_specific_tools_for_current_system():
    specs = AgentWorkflow()._planner_tool_specs("rental")
    names = {item["function"]["name"] for item in specs}

    assert "fetch_rental_dashboard" in names
    assert "query_system_logs" in names
    assert "fetch_alipay_analytics_dashboard" not in names
    assert "fetch_secondhand_analysis_dashboard" not in names


def test_rental_single_day_dashboard_selection_uses_readonly_sql_for_exact_scope():
    workflow = AgentWorkflow(sql_clients={"rental": FakeSqlClientForLoop()})
    state = {
        "system": "rental",
        "message": "分析今天设备租赁订单趋势",
        "original_message": "分析今天设备租赁订单趋势",
        "context": {},
    }

    applied = workflow._apply_llm_tool_call(
        state,
        {"name": "fetch_rental_dashboard", "args": {"question": "分析今天设备租赁订单趋势"}},
    )

    assert applied is True
    assert state["intent"] == "sql_analysis"
    assert [call["kind"] for call in state["tool_calls"]] == ["sql_schema", "sql_plan", "sql_query"]


def test_multi_tool_plan_prefers_sql_analysis_as_renderable_primary_intent():
    workflow = AgentWorkflow(sql_clients={"rental": FakeSqlClientForLoop()})
    state = {
        "system": "rental",
        "message": "分析今年设备租赁订单和租金趋势",
        "original_message": "分析今年设备租赁订单和租金趋势",
        "context": {},
    }

    applied = workflow._apply_llm_tool_calls(state, [
        {
            "name": "fetch_rental_dashboard",
            "args": {"question": "查看今年设备租赁趋势"},
            "id": "call_dashboard",
        },
        {
            "name": "query_readonly_sql",
            "args": {"question": "按真实表结构查询今年设备租赁订单和租金趋势", "target_system": "rental"},
            "id": "call_sql",
        },
    ])

    assert applied is True
    assert state["intent"] in {"sql_analysis", "device_performance"}
    assert any(call.get("kind") == "sql_query" for call in state["tool_calls"])


def test_llm_rewrite_cannot_change_explicit_current_year_scope():
    workflow = AgentWorkflow.__new__(AgentWorkflow)
    current_year = workflow._today_text()[:4]
    wrong_year = str(int(current_year) - 1)

    assert workflow._llm_question_drops_original_intent(
        "分析今年二手交易趋势",
        f"分析{wrong_year}年二手交易趋势",
    ) is True
    assert workflow._llm_question_drops_original_intent(
        "分析今年二手交易趋势",
        f"分析{current_year}年二手交易趋势",
    ) is False


def test_weather_search_query_includes_current_date_to_avoid_stale_results():
    workflow = AgentWorkflow.__new__(AgentWorkflow)

    query = workflow._web_search_query({
        "message": "今天杭州天气怎么样",
        "system": "alipay",
        "context": {},
    })

    assert workflow._today_text() in query
    assert "杭州天气" in query


def test_equipment_rental_trend_is_not_misclassified_as_device_ranking():
    workflow = AgentWorkflow.__new__(AgentWorkflow)

    assert workflow._looks_like_device_performance(
        "分析今年设备租赁订单数和租金金额，按月生成趋势图"
    ) is False
    assert workflow._looks_like_device_performance("哪些设备租得好") is True


class FakeLlmCurrentTimeClient:
    async def current_time(self, timezone_name="Asia/Shanghai"):
        assert timezone_name == "Asia/Shanghai"
        return {
            "timezone": timezone_name,
            "utcOffset": "+08:00",
            "iso": "2026-07-10T21:15:30+08:00",
            "date": "2026年07月10日",
            "time": "21:15:30",
            "weekday": "星期五",
            "display": "2026年07月10日 21:15:30（星期五）",
        }


@pytest.mark.asyncio
async def test_current_time_result_is_summarized_by_llm_with_exact_timestamp_and_follow_up():
    model = SequencedAgentLoopModel([TextResponse(
        '{"answer":"结论：MCP 返回的当前北京时间为 2026年07月10日 21:15:30（星期五）。",'
        '"followUpQuestion":"要不要同时对照 UTC？",'
        '"followUpPrompts":[{"label":"查看 UTC","prompt":"查询当前 UTC 时间"}]}'
    )])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        web_search_client=FakeLlmCurrentTimeClient(),
    )

    result = await workflow.run(
        message="现在几点",
        conversation_id="c-time-llm",
        user_token=None,
        system="alipay",
        context={},
    )

    assert "MCP 返回的当前北京时间" in result["answer"]
    assert "2026年07月10日 21:15:30（星期五）" in result["answer"]
    assert "要不要同时对照 UTC？" in result["answer"]
    assert result["workspace"]["nextPrompts"] == [{"label": "查看 UTC", "prompt": "查询当前 UTC 时间"}]
    prompt = "\n".join(str(getattr(item, "content", "")) for item in model.all_messages[0])
    assert '"display": "2026年07月10日 21:15:30（星期五）"' in prompt


@pytest.mark.asyncio
async def test_current_time_llm_cannot_replace_validated_timestamp():
    model = SequencedAgentLoopModel([TextResponse(
        '{"answer":"结论：当前北京时间为 2025年12月30日 17:13:05（星期二）。",'
        '"followUpQuestion":"还要查别的吗？"}'
    )])
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        web_search_client=FakeLlmCurrentTimeClient(),
    )

    result = await workflow.run(
        message="现在几点",
        conversation_id="c-time-llm-guard",
        user_token=None,
        system="alipay",
        context={},
    )

    assert "2026年07月10日 21:15:30（星期五）" in result["answer"]
    assert "2025年12月30日" not in result["answer"]

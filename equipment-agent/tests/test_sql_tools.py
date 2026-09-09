from __future__ import annotations

import pytest
from sqlalchemy import create_engine, text
from sqlalchemy.pool import StaticPool

from equipment_agent.agent.workflow import AgentWorkflow
from equipment_agent.config.model_config import ModelConfig
from equipment_agent.tools.sql import ReadOnlySqlClient, ReadOnlySqlPolicy


def test_readonly_sql_policy_blocks_mutation_multi_statement_and_sensitive_fields():
    policy = ReadOnlySqlPolicy()

    assert policy.check("select name from device limit 1").allowed is True
    assert policy.check("select name from device; select 1").allowed is False
    assert policy.check("update device set name='x'").allowed is False
    assert policy.check("select user_phone from rent_order").allowed is False


def test_readonly_sql_client_limits_and_masks_rows():
    engine = create_engine(
        "sqlite+pysqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    with engine.begin() as connection:
        connection.execute(text("create table revenue (name text, amount real, phone text)"))
        connection.execute(text("insert into revenue values ('A', 10.5, '13800138000'), ('B', 8.0, '13900139000')"))

    client = ReadOnlySqlClient("sqlite+pysqlite://", max_rows=1, engine=engine)

    result = client.query("select name, amount from revenue order by amount desc")

    assert result["rowCount"] == 1
    assert result["truncated"] is True
    assert result["rows"][0]["name"] == "A"
    with pytest.raises(ValueError):
        client.query("select name, phone from revenue")


def test_readonly_sql_client_lists_and_describes_dynamic_tables():
    engine = create_engine(
        "sqlite+pysqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    with engine.begin() as connection:
        connection.execute(text("create table custom_metric (id integer, metric_value integer)"))
        connection.execute(text("create table user_activity (id integer, user_id integer, active_at integer)"))

    client = ReadOnlySqlClient("sqlite+pysqlite://", max_rows=10, engine=engine)

    table_names = client.list_table_names()
    schema = client.describe_tables(table_names)

    assert "custom_metric" in table_names
    assert "user_activity" in table_names
    described = {table["name"]: table for table in schema["tables"]}
    assert described["custom_metric"]["exists"] is True
    assert {column["name"] for column in described["user_activity"]["columns"]} >= {"user_id", "active_at"}


class FakeConfigClient:
    async def load(self) -> ModelConfig:
        return ModelConfig(
            provider="ds",
            model="deepseek-chat",
            base_url="https://api.deepseek.com",
            api_key="test-key",
        )


class FakeResponse:
    def __init__(self, content: str) -> None:
        self.content = content


class SequencedModel:
    def __init__(self) -> None:
        self.messages = []

    async def ainvoke(self, messages):
        self.messages.append(messages)
        if len(self.messages) == 1:
            return FakeResponse(
                """
                {
                  "sql": "SELECT deviceName, orderCount, revenueYuan FROM device_rank ORDER BY revenueYuan DESC LIMIT 10",
                  "title": "设备表现排行",
                  "metricLabel": "收入和订单综合表现",
                  "xKey": "deviceName",
                  "valueKeys": ["revenueYuan", "orderCount"],
                  "chartType": "bar",
                  "answerFocus": "按收入优先，结合订单数解释设备表现"
                }
                """
            )
        return FakeResponse("结论：A 设备当前最好。")


class FakeSqlClient:
    def __init__(self) -> None:
        self.queries = []

    def describe_tables(self, table_names):
        return {
            "tables": [
                {
                    "name": "order",
                    "exists": True,
                    "columns": [
                        {"name": "deviceName", "type": "varchar", "nullable": True},
                        {"name": "orderCount", "type": "int", "nullable": True},
                        {"name": "revenueYuan", "type": "decimal", "nullable": True},
                    ],
                }
            ]
        }

    def query(self, sql, params=None, max_rows=None):
        self.queries.append((sql, params, max_rows))
        return {
            "rows": [
                {"deviceName": "A", "orderCount": 5, "revenueYuan": 800.0},
                {"deviceName": "B", "orderCount": 3, "revenueYuan": 360.0},
            ],
            "rowCount": 2,
            "truncated": False,
        }


class FakeDynamicSchemaSqlClient:
    def __init__(self) -> None:
        self.described_table_names = []

    def list_table_names(self):
        return ["custom_metric", "rent_order", "user_activity"]

    def describe_tables(self, table_names):
        self.described_table_names = list(table_names)
        return {
            "tables": [
                {
                    "name": name,
                    "exists": True,
                    "columns": [
                        {"name": "id", "type": "bigint", "nullable": False},
                        {"name": "metric_value", "type": "int", "nullable": True},
                        {"name": "phone", "type": "varchar", "nullable": True},
                    ],
                }
                for name in table_names
            ]
        }

    def query(self, sql, params=None, max_rows=None):
        return {"rows": [], "rowCount": 0, "truncated": False}


class FakeOrderCountSqlClient:
    def __init__(self) -> None:
        self.queries = []

    def describe_tables(self, table_names):
        return {
            "tables": [
                {
                    "name": "rent_order",
                    "exists": True,
                    "columns": [
                        {"name": "order_id", "type": "varchar", "nullable": False},
                        {"name": "created_at", "type": "bigint", "nullable": True},
                    ],
                }
            ]
        }

    def query(self, sql, params=None, max_rows=None):
        self.queries.append((sql, params, max_rows))
        return {
            "rows": [{"orderCount": 7}],
            "rowCount": 1,
            "truncated": False,
        }


class FakeOrderTrendSqlClient:
    def __init__(self) -> None:
        self.queries = []

    def describe_tables(self, table_names):
        return {
            "tables": [
                {
                    "name": "rent_order",
                    "exists": True,
                    "columns": [
                        {"name": "order_id", "type": "varchar", "nullable": False},
                        {"name": "created_at", "type": "bigint", "nullable": True},
                    ],
                }
            ]
        }

    def query(self, sql, params=None, max_rows=None):
        self.queries.append((sql, params, max_rows))
        return {
            "rows": [
                {"date": "2026-06-01", "orderCount": 1},
                {"date": "2026-06-02", "orderCount": 3},
            ],
            "rowCount": 2,
            "truncated": False,
        }


class FakeActiveUserTrendSqlClient:
    def __init__(self) -> None:
        self.queries = []

    def describe_tables(self, table_names):
        return {
            "tables": [
                {
                    "name": "rent_order",
                    "exists": True,
                    "columns": [
                        {"name": "order_id", "type": "varchar", "nullable": False},
                        {"name": "created_at", "type": "bigint", "nullable": True},
                        {"name": "user_id", "type": "bigint", "nullable": True},
                    ],
                },
                {
                    "name": "user",
                    "exists": True,
                    "columns": [
                        {"name": "user_id", "type": "bigint", "nullable": False},
                        {"name": "created_at", "type": "bigint", "nullable": True},
                    ],
                },
            ]
        }

    def query(self, sql, params=None, max_rows=None):
        self.queries.append((sql, params, max_rows))
        return {
            "rows": [
                {"date": "2026-06-01", "activeUserCount": 2},
                {"date": "2026-06-02", "activeUserCount": 5},
            ],
            "rowCount": 2,
            "truncated": False,
        }


class FakeSalesSqlClient:
    def __init__(self) -> None:
        self.queries = []

    def describe_tables(self, table_names):
        return {
            "tables": [
                {
                    "name": "rent_order",
                    "exists": True,
                    "columns": [
                        {"name": "order_id", "type": "varchar", "nullable": False},
                        {"name": "created_at", "type": "bigint", "nullable": True},
                        {"name": "paid_amount", "type": "bigint", "nullable": True},
                    ],
                }
            ]
        }

    def query(self, sql, params=None, max_rows=None):
        self.queries.append((sql, params, max_rows))
        return {
            "rows": [
                {"date": "2026-06-01", "orderCount": 1, "paidAmountYuan": 210.0},
                {"date": "2026-06-02", "orderCount": 3, "paidAmountYuan": 61.0},
            ],
            "rowCount": 2,
            "truncated": False,
        }


class FakeAmountDistributionSqlClient:
    def __init__(self) -> None:
        self.queries = []

    def describe_tables(self, table_names):
        return {
            "tables": [
                {
                    "name": "rent_order",
                    "exists": True,
                    "columns": [
                        {"name": "created_at", "type": "bigint", "nullable": True},
                        {"name": "paid_amount", "type": "bigint", "nullable": True},
                    ],
                }
            ]
        }

    def query(self, sql, params=None, max_rows=None):
        self.queries.append((sql, params or {}, max_rows))
        return {
            "rows": [
                {"amountRange": "0-100元", "orderCount": 8, "paidAmountYuan": 320.0},
                {"amountRange": "100-1000元", "orderCount": 4, "paidAmountYuan": 2400.0},
            ],
            "rowCount": 2,
            "truncated": False,
        }


class FakeOrderStatusSqlClient:
    def __init__(self) -> None:
        self.queries = []

    def describe_tables(self, table_names):
        return {
            "tables": [
                {
                    "name": "rent_order",
                    "exists": True,
                    "columns": [
                        {"name": "order_id", "type": "varchar", "nullable": False},
                        {"name": "order_no", "type": "varchar", "nullable": True},
                        {"name": "status", "type": "int", "nullable": True},
                        {"name": "alipay_status", "type": "varchar", "nullable": True},
                        {"name": "paid_amount", "type": "bigint", "nullable": True},
                        {"name": "total_amount", "type": "bigint", "nullable": True},
                        {"name": "created_at", "type": "bigint", "nullable": True},
                    ],
                }
            ]
        }

    def query(self, sql, params=None, max_rows=None):
        self.queries.append((sql, params, max_rows))
        return {
            "rows": [
                {
                    "orderStatus": "已支付",
                    "localStatus": 1,
                    "alipayStatus": "PAID",
                    "paidAmountYuan": 1.0,
                    "totalAmountYuan": 1.0,
                    "createdAt": 1783526438653,
                }
            ],
            "rowCount": 1,
            "truncated": False,
        }


@pytest.mark.asyncio
async def test_workflow_uses_dynamic_sql_toolchain_when_db_is_configured():
    model = SequencedModel()
    sql_client = FakeSqlClient()
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: model,
        sql_clients={"rental": sql_client},
    )

    result = await workflow.run(
        message="告诉我哪个设备最好，并展示理由",
        conversation_id="c1",
        user_token=None,
        system="rental",
        context={},
    )

    names = [call["name"] for call in result["toolCalls"]]
    assert names[:3] == ["inspect_rental_business_schema", "plan_rental_readonly_sql", "execute_rental_readonly_sql"]
    assert [call["status"] for call in result["toolCalls"][:3]] == ["success", "success", "success"]
    assert sql_client.queries
    assert sql_client.queries[0][0] == (
        "SELECT deviceName, orderCount, revenueYuan FROM device_rank "
        "ORDER BY revenueYuan DESC LIMIT 10"
    )
    assert result["charts"][0]["sourceTool"] == "execute_rental_readonly_sql"
    assert result["charts"][0]["rows"][0]["deviceName"] == "A"
    assert result["answer"] == "结论：A 设备当前最好。"


@pytest.mark.asyncio
async def test_marketing_device_question_uses_device_performance_not_order_status():
    sql_client = FakeSqlClient()
    workflow = AgentWorkflow(sql_clients={"alipay": sql_client})

    result = await workflow.run(
        message="我是否需要开启营销呢，或者哪些设备租的好",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    names = [call["name"] for call in result["toolCalls"]]
    assert names[:3] == ["inspect_alipay_business_schema", "plan_alipay_readonly_sql", "execute_alipay_readonly_sql"]
    assert [call["status"] for call in result["toolCalls"][:3]] == ["success", "success", "success"]
    sql, _, _ = sql_client.queries[0]
    assert "goods_title" in sql
    assert "订单支付状态" not in result["answer"]
    assert "优先围绕「A」开启营销" in result["answer"]
    assert result["charts"][0]["title"] == "支付宝租赁设备表现排行"


def test_order_ref_extractor_does_not_treat_plain_words_as_order_ids():
    workflow = AgentWorkflow()

    assert workflow._extract_order_ref("localStatus createdAt totalAmountYuan") == ""
    assert workflow._extract_order_ref("178352643865325 看下订单是否支付") == "178352643865325"
    assert workflow._extract_order_ref("订单号 A202607090001 看下状态") == "A202607090001"


@pytest.mark.asyncio
async def test_workflow_schema_tool_discovers_all_tables_from_database():
    sql_client = FakeDynamicSchemaSqlClient()
    workflow = AgentWorkflow(sql_clients={"alipay": sql_client})

    result = await workflow.run(
        message="查看自定义指标统计",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    assert "custom_metric" in sql_client.described_table_names
    assert "user_activity" in sql_client.described_table_names
    schema = next(call for call in result["toolCalls"] if call["kind"] == "sql_schema")
    assert schema["tableCount"] == 3


@pytest.mark.asyncio
async def test_workflow_uses_static_order_count_sql_without_llm():
    sql_client = FakeOrderCountSqlClient()
    workflow = AgentWorkflow(sql_clients={"alipay": sql_client})

    result = await workflow.run(
        message="我想知道这个月有多少订单",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    names = [call["name"] for call in result["toolCalls"]]
    assert names[:3] == ["inspect_alipay_business_schema", "plan_alipay_readonly_sql", "execute_alipay_readonly_sql"]
    assert [call["status"] for call in result["toolCalls"][:3]] == ["success", "success", "success"]
    sql, params, _ = sql_client.queries[0]
    assert "COUNT(1) AS orderCount" in sql
    assert "rent_order" in sql
    assert "startMs" in params
    assert "endMs" in params
    assert "订单数 7" in result["answer"]


@pytest.mark.asyncio
async def test_workflow_uses_static_order_trend_sql_without_llm():
    sql_client = FakeOrderTrendSqlClient()
    workflow = AgentWorkflow(sql_clients={"alipay": sql_client})

    result = await workflow.run(
        message="近 30 天订单趋势",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    names = [call["name"] for call in result["toolCalls"]]
    assert names[:3] == ["inspect_alipay_business_schema", "plan_alipay_readonly_sql", "execute_alipay_readonly_sql"]
    assert [call["status"] for call in result["toolCalls"][:3]] == ["success", "success", "success"]
    sql, params, _ = sql_client.queries[0]
    assert "GROUP BY DATE(FROM_UNIXTIME(created_at / 1000))" in sql
    assert "COUNT(1) AS orderCount" in sql
    assert "startMs" in params
    assert "endMs" in params
    assert result["charts"][0]["type"] == "line"
    assert result["charts"][0]["rows"][0]["orderCount"] == 1
    assert "订单数 4" in result["answer"]


@pytest.mark.asyncio
async def test_workflow_resolves_vague_trend_followup_from_order_context():
    sql_client = FakeOrderTrendSqlClient()
    workflow = AgentWorkflow(sql_clients={"alipay": sql_client})

    result = await workflow.run(
        message="近 30 天趋势",
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

    sql, params, _ = sql_client.queries[0]
    assert "GROUP BY DATE(FROM_UNIXTIME(created_at / 1000))" in sql
    assert "COUNT(1) AS orderCount" in sql
    assert params["endMs"] > params["startMs"]
    assert result["questions"] == []
    assert result["charts"][0]["type"] == "line"
    assert "订单数 4" in result["answer"]


@pytest.mark.asyncio
async def test_workflow_uses_static_active_user_trend_sql_without_sensitive_fields():
    sql_client = FakeActiveUserTrendSqlClient()
    workflow = AgentWorkflow(sql_clients={"alipay": sql_client})

    result = await workflow.run(
        message="查看近 30 天用户活跃数趋势",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    names = [call["name"] for call in result["toolCalls"]]
    assert names[:3] == ["inspect_alipay_business_schema", "plan_alipay_readonly_sql", "execute_alipay_readonly_sql"]
    assert [call["status"] for call in result["toolCalls"][:3]] == ["success", "success", "success"]
    sql, params, _ = sql_client.queries[0]
    assert "COUNT(DISTINCT user_id) AS activeUserCount" in sql
    assert "phone" not in sql.lower()
    assert "id_card" not in sql.lower()
    assert "startMs" in params
    assert "endMs" in params
    assert result["charts"][0]["type"] == "line"
    assert result["charts"][0]["rows"][0]["activeUserCount"] == 2
    assert "活跃用户数 7" in result["answer"]


@pytest.mark.asyncio
async def test_workflow_uses_static_alipay_sales_sql_with_yuan_conversion():
    sql_client = FakeSalesSqlClient()
    workflow = AgentWorkflow(sql_clients={"alipay": sql_client})

    result = await workflow.run(
        message="我想知道我6月份的销售情况",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    names = [call["name"] for call in result["toolCalls"]]
    assert names[:3] == ["inspect_alipay_business_schema", "plan_alipay_readonly_sql", "execute_alipay_readonly_sql"]
    assert [call["status"] for call in result["toolCalls"][:3]] == ["success", "success", "success"]
    sql, params, _ = sql_client.queries[0]
    assert "SUM(COALESCE(paid_amount, 0)) / 100" in sql
    assert "paidAmountYuan" in sql
    assert "startMs" in params
    assert "endMs" in params
    assert result["charts"][0]["rows"][0]["paidAmountYuan"] == 210.0
    assert "订单数 4" in result["answer"]
    assert "已支付金额（元） 271.00 元" in result["answer"]
    assert "已支付金额（元）" in result["answer"]
    assert "证据" not in result["answer"]
    assert "判断理由" not in result["answer"]
    assert "后续建议" not in result["answer"]
    assert "后续规划" not in result["answer"]
    assert len(result["charts"]) >= 2
    assert result["charts"][0]["type"] == "line"
    assert result["charts"][0]["summary"] == "订单数 4，已支付金额（元） 271.00 元"
    assert result["charts"][0]["unitLabels"]["paidAmountYuan"] == "元"
    assert result["charts"][1]["type"] == "stat"
    assert result["charts"][1]["rows"][0]["paidAmountYuan"] == 271.0


@pytest.mark.asyncio
async def test_workflow_builds_amount_distribution_pie_chart():
    sql_client = FakeAmountDistributionSqlClient()
    workflow = AgentWorkflow(sql_clients={"alipay": sql_client})

    result = await workflow.run(
        message="看下6月份订单金额分布",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    sql, params, _ = sql_client.queries[0]
    assert "CASE" in sql
    assert "amountRange" in sql
    assert "startMs" in params
    assert result["charts"][0]["type"] == "pie"
    assert result["charts"][0]["dimensions"][0] == "amountRange"
    assert result["charts"][0]["series"][0]["key"] == "orderCount"


@pytest.mark.asyncio
async def test_workflow_uses_fast_order_status_sql_without_llm_planner():
    class FailIfCalledModel:
        def bind_tools(self, *args, **kwargs):
            raise AssertionError("LLM planner should not run for deterministic order status lookup")

        async def ainvoke(self, messages):
            raise AssertionError("LLM reply should not run when SQL chart result is available")

    sql_client = FakeOrderStatusSqlClient()
    workflow = AgentWorkflow(
        model_config_client=FakeConfigClient(),
        llm_factory=lambda config: FailIfCalledModel(),
        sql_clients={"alipay": sql_client},
    )

    result = await workflow.run(
        message="178352643865325 看下这个订单是不是已经支付了",
        conversation_id="c1",
        user_token=None,
        system="alipay",
        context={},
    )

    names = [call["name"] for call in result["toolCalls"]]
    assert names[:3] == ["inspect_alipay_business_schema", "plan_alipay_readonly_sql", "execute_alipay_readonly_sql"]
    sql, params, _ = sql_client.queries[0]
    assert "order_no = :orderRef" in sql
    assert "order_id = :orderRef" in sql
    assert params == {"orderRef": "178352643865325"}
    assert "已支付" in result["answer"]
    assert "SELECT" not in result["answer"]
    assert "查询依据" not in result["answer"]
    assert "证据" not in result["answer"]
    assert "判断理由" not in result["answer"]


@pytest.mark.asyncio
async def test_workflow_resolves_relative_followup_from_conversation_history():
    sql_client = FakeOrderCountSqlClient()
    workflow = AgentWorkflow(sql_clients={"alipay": sql_client})

    result = await workflow.run(
        message="去年呢",
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

    assert result["toolCalls"][0]["status"] == "success"
    assert result["toolCalls"][1]["status"] == "success"
    sql, params, _ = sql_client.queries[0]
    assert "COUNT(1) AS orderCount" in sql
    assert params["endMs"] > params["startMs"]
    assert "订单数 7" in result["answer"]

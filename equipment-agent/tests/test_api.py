import json
import asyncio

from fastapi.testclient import TestClient

from equipment_agent.agent.workflow import AgentWorkflow
from equipment_agent.agent.run_registry import RunRegistry
from equipment_agent.api.app import create_app
from equipment_agent.memory.store import MemoryStore


def create_test_client() -> TestClient:
    return TestClient(create_app(memory_store=MemoryStore(), workflow=AgentWorkflow()))


class RecordingWorkflow:
    def __init__(self) -> None:
        self.contexts = []
        self.owner_ids = []

    async def run(self, message, conversation_id, user_token=None, system="alipay", context=None, **kwargs):
        self.contexts.append(context or {})
        self.owner_ids.append(kwargs.get("owner_id"))
        return {
            "answer": "ok",
            "toolCalls": [],
            "charts": [],
            "sources": [{"title": "公开来源", "url": "https://example.com", "source": "example.com", "rank": 1}],
            "questions": [{"question": "你想看哪个趋势？", "options": ["订单数趋势"]}],
            "workspace": {"readonly": True},
            "contextSnapshot": {"topic": "订单分析", "timeRange": "本月", "system": system},
            "reports": [],
        }


class SlowStreamingWorkflow:
    async def run(self, message, conversation_id, user_token=None, system="alipay", context=None, **kwargs):
        await asyncio.sleep(0.02)
        result = {
            "system": system,
            "answer": "ok",
            "toolCalls": [],
            "charts": [],
            "questions": [],
            "workspace": {"readonly": True},
            "reports": [],
        }
        await kwargs["event_emitter"].emit("run.completed", result)
        return result


def parse_sse_events(content: str) -> list[tuple[str, dict]]:
    events = []
    for block in content.strip().split("\n\n"):
        event_type = ""
        payload = {}
        for line in block.splitlines():
            if line.startswith("event: "):
                event_type = line[7:]
            elif line.startswith("data: "):
                payload = json.loads(line[6:])
        if event_type:
            events.append((event_type, payload))
    return events


def test_chat_endpoint_returns_readonly_answer_and_audit_tools():
    client = create_test_client()

    response = client.post(
        "/api/agent/chat",
        headers={"token": "user-token"},
        json={"message": "查一下支付宝近7天订单趋势"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert body["data"]["conversationId"]
    assert body["data"]["system"] == "alipay"
    assert "只读" in body["data"]["answer"]
    assert body["data"]["toolCalls"]
    assert body["data"]["workspace"]["readonly"] is True
    assert {item["title"] for item in body["data"]["workspace"]["lanes"]} >= {"今日待处理", "异常订单", "同步失败", "风控预警", "收益异动"}


def test_chat_endpoint_routes_by_business_system():
    client = create_test_client()

    response = client.post(
        "/api/agent/chat",
        json={"message": "生成近7天订单趋势", "system": "secondhand"},
    )

    assert response.status_code == 200
    data = response.json()["data"]
    assert data["system"] == "secondhand"
    assert data["toolCalls"][0]["system"] == "secondhand"
    assert data["toolCalls"][0]["name"] == "fetch_secondhand_analysis_dashboard"
    assert data["toolCalls"][0]["status"] == "skipped"
    assert data["charts"] == []
    assert data["workspace"]["summary"]["title"] == "二手交易业务工作台"


def test_conversations_endpoint_returns_list():
    client = create_test_client()

    response = client.get("/api/agent/conversations")

    assert response.status_code == 200
    body = response.json()
    assert body["code"] == 0
    assert isinstance(body["data"]["items"], list)


def test_conversation_history_is_filtered_by_system():
    client = create_test_client()

    client.post("/api/agent/chat", json={"message": "查支付宝订单", "system": "alipay"})
    client.post("/api/agent/chat", json={"message": "查二手订单", "system": "secondhand"})

    alipay = client.get("/api/agent/conversations", params={"system": "alipay"}).json()["data"]["items"]
    secondhand = client.get("/api/agent/conversations", params={"system": "secondhand"}).json()["data"]["items"]

    assert len(alipay) == 1
    assert len(secondhand) == 1
    assert alipay[0]["system"] == "alipay"
    assert secondhand[0]["system"] == "secondhand"


def test_conversation_history_is_isolated_by_gateway_user_context():
    client = create_test_client()
    user_one = {"X-Login-User-Id": "101"}
    user_two = {"X-Login-User-Id": "202"}

    response = client.post(
        "/api/agent/chat",
        headers=user_one,
        json={"message": "查我的订单", "system": "alipay"},
    )
    conversation_id = response.json()["data"]["conversationId"]

    assert len(client.get("/api/agent/conversations", headers=user_one).json()["data"]["items"]) == 1
    assert client.get("/api/agent/conversations", headers=user_two).json()["data"]["items"] == []
    assert client.get(
        f"/api/agent/conversations/{conversation_id}",
        headers=user_two,
    ).json()["data"]["messages"] == []
    assert client.post(
        "/api/agent/chat",
        headers=user_two,
        json={"message": "接着查", "conversationId": conversation_id, "system": "alipay"},
    ).status_code == 403
    assert client.delete(
        f"/api/agent/conversations/{conversation_id}",
        headers=user_two,
    ).json()["data"]["deleted"] is False
    assert client.delete(
        f"/api/agent/conversations/{conversation_id}",
        headers=user_one,
    ).json()["data"]["deleted"] is True


def test_chat_passes_gateway_owner_to_agent_memory_scope():
    workflow = RecordingWorkflow()
    client = TestClient(create_app(memory_store=MemoryStore(), workflow=workflow))

    response = client.post(
        "/api/agent/chat",
        headers={"X-Login-User-Id": "101"},
        json={"message": "查我的订单", "system": "alipay"},
    )

    assert response.status_code == 200
    assert workflow.owner_ids == ["101"]


def test_conversation_history_keeps_workspace_metadata():
    client = create_test_client()

    response = client.post("/api/agent/chat", json={"message": "查支付宝同步失败", "system": "alipay"})
    conversation_id = response.json()["data"]["conversationId"]

    detail = client.get(f"/api/agent/conversations/{conversation_id}", params={"system": "alipay"}).json()["data"]
    assistant = detail["messages"][1]

    assert assistant["role"] == "assistant"
    assert assistant["workspace"]["readonly"] is True
    assert assistant["workspace"]["actionDrafts"]


def test_delete_conversation_removes_history():
    client = create_test_client()

    response = client.post("/api/agent/chat", json={"message": "你好", "system": "alipay"})
    conversation_id = response.json()["data"]["conversationId"]

    delete_response = client.delete(f"/api/agent/conversations/{conversation_id}", params={"system": "alipay"})

    assert delete_response.status_code == 200
    assert delete_response.json()["data"]["deleted"] is True
    assert client.get("/api/agent/conversations", params={"system": "alipay"}).json()["data"]["items"] == []
    assert client.get(f"/api/agent/conversations/{conversation_id}", params={"system": "alipay"}).json()["data"]["messages"] == []


def test_chat_endpoint_passes_previous_turns_as_context():
    store = MemoryStore()
    workflow = RecordingWorkflow()
    client = TestClient(create_app(memory_store=store, workflow=workflow))

    response = client.post("/api/agent/chat", json={"message": "我想知道这个月有多少订单", "system": "alipay"})
    conversation_id = response.json()["data"]["conversationId"]
    client.post(
        "/api/agent/chat",
        json={"message": "去年呢", "conversationId": conversation_id, "system": "alipay"},
    )

    history = workflow.contexts[-1]["conversationHistory"]
    assert history[-2]["role"] == "user"
    assert history[-2]["content"] == "我想知道这个月有多少订单"
    assert workflow.contexts[-1]["conversationContext"]["topic"] == "订单分析"


def test_chat_endpoint_returns_and_persists_sources():
    store = MemoryStore()
    workflow = RecordingWorkflow()
    client = TestClient(create_app(memory_store=store, workflow=workflow))

    response = client.post("/api/agent/chat", json={"message": "查公开资料", "system": "alipay"})
    data = response.json()["data"]
    conversation_id = data["conversationId"]

    assert data["sources"][0]["source"] == "example.com"
    assert data["contextSnapshot"]["topic"] == "订单分析"
    detail = client.get(f"/api/agent/conversations/{conversation_id}", params={"system": "alipay"}).json()["data"]
    assert detail["messages"][-1]["sources"][0]["title"] == "公开来源"


def test_chat_endpoint_persists_questions_metadata():
    store = MemoryStore()
    workflow = RecordingWorkflow()
    client = TestClient(create_app(memory_store=store, workflow=workflow))

    response = client.post("/api/agent/chat", json={"message": "近 30 天趋势", "system": "alipay"})
    data = response.json()["data"]
    conversation_id = data["conversationId"]

    assert data["questions"][0]["question"] == "你想看哪个趋势？"
    detail = client.get(f"/api/agent/conversations/{conversation_id}", params={"system": "alipay"}).json()["data"]
    assistant = detail["messages"][1]
    assert assistant["questions"][0]["options"] == ["订单数趋势"]


def test_stream_endpoint_emits_run_lifecycle_and_persists_result():
    store = MemoryStore()
    client = TestClient(create_app(memory_store=store, workflow=AgentWorkflow()))

    response = client.post(
        "/api/agent/chat/stream",
        json={"message": "你好", "system": "alipay"},
    )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    events = parse_sse_events(response.text)
    event_types = [event_type for event_type, _ in events]
    assert event_types[0] == "run.started"
    assert "plan.ready" in event_types
    assert "answer.delta" in event_types
    assert event_types[-1] == "run.completed"
    completed = events[-1][1]
    assert completed["runId"]
    conversation_id = completed["data"]["conversationId"]
    detail = client.get(f"/api/agent/conversations/{conversation_id}", params={"system": "alipay"}).json()["data"]
    assert detail["messages"][0]["content"] == "你好"
    assert detail["messages"][1]["role"] == "assistant"


def test_cancel_endpoint_reports_unknown_run_without_side_effects():
    client = create_test_client()

    response = client.post("/api/agent/runs/missing-run/cancel")

    assert response.status_code == 200
    assert response.json()["data"] == {"runId": "missing-run", "cancelled": False}


def test_existing_chat_route_streams_when_accept_header_requests_sse():
    client = create_test_client()

    response = client.post(
        "/api/agent/chat",
        headers={"Accept": "text/event-stream"},
        json={"message": "你好", "system": "alipay"},
    )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    assert [event_type for event_type, _ in parse_sse_events(response.text)][-1] == "run.completed"


def test_stream_sends_keepalive_comments_during_long_tool_calls(monkeypatch):
    monkeypatch.setattr("equipment_agent.api.app.STREAM_HEARTBEAT_SECONDS", 0.005)
    client = TestClient(create_app(memory_store=MemoryStore(), workflow=SlowStreamingWorkflow()))

    response = client.post(
        "/api/agent/chat",
        headers={"Accept": "text/event-stream"},
        json={"message": "慢查询", "system": "alipay"},
    )

    assert response.status_code == 200
    assert ": keep-alive\n\n" in response.text
    assert [event_type for event_type, _ in parse_sse_events(response.text)][-1] == "run.completed"


def test_existing_chat_route_cancels_active_run_action():
    registry = RunRegistry()
    handle = asyncio.run(registry.start("active-run"))
    client = TestClient(create_app(memory_store=MemoryStore(), workflow=AgentWorkflow(), run_registry=registry))

    response = client.post(
        "/api/agent/chat",
        json={"message": "停止", "action": "cancel", "runId": "active-run", "system": "alipay"},
    )

    assert response.status_code == 200
    assert response.json()["data"] == {"runId": "active-run", "cancelled": True}
    assert handle.cancellation.cancelled is True

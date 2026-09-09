from qdrant_client import QdrantClient

from equipment_agent.memory.store import SqliteMemoryStore
from equipment_agent.memory.store import MemoryStore
from equipment_agent.memory.vector_store import VectorMemoryStore


def test_vector_memory_stores_and_searches_agent_memories():
    client = QdrantClient(":memory:")
    store = VectorMemoryStore(client=client, collection_name="agent_memories_test")

    store.remember(
        text="支付宝订单 A001 支付回调未匹配到本地订单，需要查询 order-oper-logs 和系统 error 日志",
        metadata={"conversationId": "c1", "system": "alipay", "type": "diagnosis"},
    )
    store.remember(
        text="二手订单同步需要检查抖音 API 日志和消息日志",
        metadata={"conversationId": "c2", "system": "secondhand", "type": "diagnosis"},
    )

    rows = store.search("支付宝支付回调日志", limit=1, system="alipay")

    assert len(rows) == 1
    assert rows[0].text.startswith("支付宝订单")
    assert rows[0].metadata["conversationId"] == "c1"
    assert rows[0].score > 0
    secondhand_rows = store.search("支付宝支付回调日志", limit=5, system="secondhand")
    assert secondhand_rows
    assert {row.metadata["system"] for row in secondhand_rows} == {"secondhand"}


def test_vector_memory_keeps_agent_storage_isolated_by_collection():
    client = QdrantClient(":memory:")
    first = VectorMemoryStore(client=client, collection_name="agent_memories_a")
    second = VectorMemoryStore(client=client, collection_name="agent_memories_b")

    first.remember("支付宝押金扣款异常", {"conversationId": "a"})

    assert first.search("押金", limit=5)
    assert second.search("押金", limit=5) == []


def test_memory_store_writes_turns_to_vector_memory():
    client = QdrantClient(":memory:")
    vector_store = VectorMemoryStore(client=client, collection_name="agent_turn_memory_test")
    store = MemoryStore(vector_store=vector_store)

    store.append_turn("c3", "查询近 7 天二手订单", {"answer": "已生成二手订单趋势分析"}, system="secondhand")

    rows = vector_store.search("二手订单趋势", limit=3, system="secondhand")
    roles = {row.metadata["role"] for row in rows}
    assert roles & {"user", "assistant"}
    assert {row.metadata["system"] for row in rows} == {"secondhand"}
    assert vector_store.search("二手订单趋势", limit=3, system="alipay") == []


def test_vector_memory_isolates_conversations_by_owner_and_keeps_public_knowledge():
    vector_store = VectorMemoryStore(collection_name="agent_owner_memory_test")
    vector_store.remember(
        "订单诊断公共说明",
        {"system": "alipay", "title": "公共知识"},
    )
    vector_store.remember(
        "订单诊断用户一记录",
        {"system": "alipay", "conversationId": "c-user-1", "ownerId": "101"},
    )
    vector_store.remember(
        "订单诊断用户二记录",
        {"system": "alipay", "conversationId": "c-user-2", "ownerId": "202"},
    )
    vector_store.remember(
        "订单诊断旧版会话记录",
        {"system": "alipay", "conversationId": "legacy-unowned"},
    )

    rows = vector_store.search("订单诊断", limit=10, system="alipay", owner_id="101")
    metadata = [row.metadata for row in rows]

    assert any(item.get("title") == "公共知识" for item in metadata)
    assert any(item.get("ownerId") == "101" for item in metadata)
    assert all(item.get("ownerId") != "202" for item in metadata)
    assert all(item.get("conversationId") != "legacy-unowned" for item in metadata)


def test_sqlite_memory_store_persists_and_deletes_conversations(tmp_path):
    db_path = tmp_path / "agent-memory.sqlite3"
    first = SqliteMemoryStore(str(db_path))

    first.append_turn("c4", "你好", {"answer": "可以正常聊天", "workspace": {"mode": "chat"}}, system="alipay")

    second = SqliteMemoryStore(str(db_path))
    conversations = second.list_conversations(system="alipay")
    assert len(conversations) == 1
    assert conversations[0]["id"] == "c4"

    detail = second.get_conversation("c4", system="alipay")
    assert [message["role"] for message in detail["messages"]] == ["user", "assistant"]
    assert detail["messages"][1]["workspace"]["mode"] == "chat"

    assert second.delete_conversation("c4", system="alipay") is True
    assert second.list_conversations(system="alipay") == []
    assert second.get_conversation("c4", system="alipay")["messages"] == []


def test_sqlite_memory_store_isolates_conversations_by_owner(tmp_path):
    db_path = tmp_path / "agent-owner-memory.sqlite3"
    store = SqliteMemoryStore(str(db_path))

    store.append_turn(
        "owned-conversation",
        "我的订单怎么样",
        {"answer": "订单状态正常"},
        system="alipay",
        owner_id="101",
    )

    assert len(store.list_conversations(system="alipay", owner_id="101")) == 1
    assert store.list_conversations(system="alipay", owner_id="202") == []
    assert store.get_conversation("owned-conversation", owner_id="202")["messages"] == []
    assert store.can_access_conversation("owned-conversation", owner_id="202") is False
    assert store.delete_conversation("owned-conversation", owner_id="202") is False
    assert store.delete_conversation("owned-conversation", owner_id="101") is True


def test_sqlite_memory_store_persists_sources_and_context(tmp_path):
    db_path = tmp_path / "agent-context.sqlite3"
    store = SqliteMemoryStore(str(db_path))

    store.append_turn(
        "c5",
        "今天杭州天气",
        {
            "answer": "杭州今天有雨。",
            "sources": [
                {
                    "title": "杭州天气",
                    "url": "https://example.com/weather",
                    "snippet": "今天有阵雨。",
                    "source": "example.com",
                    "rank": 1,
                }
            ],
            "contextSnapshot": {"topic": "天气", "location": "杭州", "timeRange": "今天"},
        },
        system="alipay",
    )

    detail = SqliteMemoryStore(str(db_path)).get_conversation("c5", system="alipay")
    assert detail["contextSnapshot"]["location"] == "杭州"
    assert detail["messages"][-1]["sources"][0]["source"] == "example.com"

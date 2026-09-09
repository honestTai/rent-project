from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from equipment_agent.memory.vector_store import VectorMemoryStore


class MemoryStore:
    """In-process store used by tests and local dev.

    Production deployments should replace this with the MySQL implementation
    configured by DATABASE_URL while keeping the same methods.
    """

    def __init__(self, vector_store: VectorMemoryStore | None = None) -> None:
        self._conversations: dict[str, dict[str, Any]] = {}
        self._reports: dict[str, dict[str, Any]] = {}
        self._vector_store = vector_store

    @property
    def vector_store(self) -> VectorMemoryStore | None:
        return self._vector_store

    def append_turn(
        self,
        conversation_id: str,
        message: str,
        result: dict[str, Any],
        system: str = "alipay",
        owner_id: str = "legacy",
    ) -> None:
        now = datetime.now(timezone.utc).isoformat()
        conversation = self._conversations.setdefault(
            conversation_id,
            {
                "id": conversation_id,
                "title": message[:40] or "新对话",
                "system": system,
                "ownerId": owner_id,
                "updatedAt": now,
                "messages": [],
            },
        )
        if conversation.get("ownerId", "legacy") != owner_id:
            raise PermissionError("conversation owner mismatch")
        conversation["system"] = system
        conversation["updatedAt"] = now
        conversation["contextSnapshot"] = dict(
            result.get("contextSnapshot") or conversation.get("contextSnapshot") or {}
        )
        conversation["messages"].append({"role": "user", "content": message, "createdAt": now})
        conversation["messages"].append({
            "role": "assistant",
            "content": result["answer"],
            "createdAt": now,
            "toolCalls": result.get("toolCalls", []),
            "charts": result.get("charts", []),
            "sources": result.get("sources", []),
            "questions": result.get("questions", []),
            "workspace": result.get("workspace", {}),
            "reports": result.get("reports", []),
        })
        if self._vector_store:
            self._vector_store.remember(
                message,
                {
                    "conversationId": conversation_id,
                    "system": system,
                    "memoryNamespace": f"{owner_id}:{system}:{conversation_id}",
                    "ownerId": owner_id,
                    "role": "user",
                    "createdAt": now,
                },
            )
            self._vector_store.remember(
                str(result["answer"]),
                {
                    "conversationId": conversation_id,
                    "system": system,
                    "memoryNamespace": f"{owner_id}:{system}:{conversation_id}",
                    "ownerId": owner_id,
                    "role": "assistant",
                    "createdAt": now,
                },
            )

    def list_conversations(self, system: str | None = None, owner_id: str = "legacy") -> list[dict[str, str]]:
        rows = []
        for item in self._conversations.values():
            if system and item.get("system") != system:
                continue
            if item.get("ownerId", "legacy") != owner_id:
                continue
            rows.append({
                "id": item["id"],
                "title": item["title"],
                "system": item.get("system", "alipay"),
                "updatedAt": item["updatedAt"],
            })
        return sorted(rows, key=lambda row: row["updatedAt"], reverse=True)

    def get_conversation(
        self,
        conversation_id: str,
        system: str | None = None,
        owner_id: str = "legacy",
    ) -> dict[str, Any]:
        conversation = self._conversations.get(conversation_id)
        if conversation is None:
            return {"id": conversation_id, "messages": []}
        if system and conversation.get("system") != system:
            return {"id": conversation_id, "messages": []}
        if conversation.get("ownerId", "legacy") != owner_id:
            return {"id": conversation_id, "messages": []}
        return conversation

    def get_report(self, report_id: str) -> dict[str, Any]:
        return self._reports.get(report_id, {"id": report_id, "status": "missing"})

    def can_access_conversation(self, conversation_id: str, owner_id: str = "legacy") -> bool:
        conversation = self._conversations.get(conversation_id)
        return conversation is None or conversation.get("ownerId", "legacy") == owner_id

    def delete_conversation(
        self,
        conversation_id: str,
        system: str | None = None,
        owner_id: str = "legacy",
    ) -> bool:
        conversation = self._conversations.get(conversation_id)
        if conversation is None:
            return False
        if system and conversation.get("system") != system:
            return False
        if conversation.get("ownerId", "legacy") != owner_id:
            return False
        del self._conversations[conversation_id]
        return True


class SqliteMemoryStore:
    """SQLite-backed conversation history with optional Qdrant memory writes."""

    def __init__(self, database_path: str, vector_store: VectorMemoryStore | None = None) -> None:
        self.database_path = database_path
        self._vector_store = vector_store
        db_file = Path(database_path)
        if db_file.parent and str(db_file.parent) not in {"", "."}:
            db_file.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    @property
    def vector_store(self) -> VectorMemoryStore | None:
        return self._vector_store

    def append_turn(
        self,
        conversation_id: str,
        message: str,
        result: dict[str, Any],
        system: str = "alipay",
        owner_id: str = "legacy",
    ) -> None:
        now = datetime.now(timezone.utc).isoformat()
        title = message[:40] or "新对话"
        with self._connect() as conn:
            existing = conn.execute(
                "SELECT owner_id FROM agent_conversations WHERE id = ?",
                (conversation_id,),
            ).fetchone()
            if existing is not None and existing["owner_id"] != owner_id:
                raise PermissionError("conversation owner mismatch")
            conn.execute(
                """
                INSERT INTO agent_conversations(id, owner_id, system, title, created_at, updated_at, context_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    system = excluded.system,
                    updated_at = excluded.updated_at,
                    context_snapshot = excluded.context_snapshot
                """,
                (
                    conversation_id,
                    owner_id,
                    system,
                    title,
                    now,
                    now,
                    _json_dumps(result.get("contextSnapshot", {})),
                ),
            )
            conn.execute(
                """
                INSERT INTO agent_messages(
                    conversation_id, system, role, content, created_at,
                    tool_calls, charts, sources, questions, workspace, reports
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (conversation_id, system, "user", message, now, "[]", "[]", "[]", "[]", "{}", "[]"),
            )
            conn.execute(
                """
                INSERT INTO agent_messages(
                    conversation_id, system, role, content, created_at,
                    tool_calls, charts, sources, questions, workspace, reports
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    conversation_id,
                    system,
                    "assistant",
                    str(result["answer"]),
                    now,
                    _json_dumps(result.get("toolCalls", [])),
                    _json_dumps(result.get("charts", [])),
                    _json_dumps(result.get("sources", [])),
                    _json_dumps(result.get("questions", [])),
                    _json_dumps(result.get("workspace", {})),
                    _json_dumps(result.get("reports", [])),
                ),
            )
        self._remember_turn(conversation_id, message, str(result["answer"]), system, owner_id, now)

    def list_conversations(self, system: str | None = None, owner_id: str = "legacy") -> list[dict[str, str]]:
        sql = "SELECT id, title, system, updated_at FROM agent_conversations WHERE owner_id = ?"
        params: tuple[Any, ...] = (owner_id,)
        if system:
            sql += " AND system = ?"
            params = (owner_id, system)
        sql += " ORDER BY updated_at DESC"
        with self._connect() as conn:
            rows = conn.execute(sql, params).fetchall()
        return [
            {
                "id": row["id"],
                "title": row["title"],
                "system": row["system"],
                "updatedAt": row["updated_at"],
            }
            for row in rows
        ]

    def get_conversation(
        self,
        conversation_id: str,
        system: str | None = None,
        owner_id: str = "legacy",
    ) -> dict[str, Any]:
        params: tuple[Any, ...] = (conversation_id, owner_id)
        conversation_sql = "SELECT id, title, system, updated_at, context_snapshot FROM agent_conversations WHERE id = ? AND owner_id = ?"
        if system:
            conversation_sql += " AND system = ?"
            params = (conversation_id, owner_id, system)
        with self._connect() as conn:
            conversation = conn.execute(conversation_sql, params).fetchone()
            if conversation is None:
                return {"id": conversation_id, "messages": []}
            rows = conn.execute(
                """
                SELECT role, content, created_at, tool_calls, charts, sources, questions, workspace, reports
                FROM agent_messages
                WHERE conversation_id = ? AND system = ?
                ORDER BY id ASC
                """,
                (conversation_id, conversation["system"]),
            ).fetchall()
        return {
            "id": conversation["id"],
            "title": conversation["title"],
            "system": conversation["system"],
            "updatedAt": conversation["updated_at"],
            "contextSnapshot": _json_loads(conversation["context_snapshot"], {}),
            "messages": [_message_from_row(row) for row in rows],
        }

    def get_report(self, report_id: str) -> dict[str, Any]:
        return {"id": report_id, "status": "missing"}

    def can_access_conversation(self, conversation_id: str, owner_id: str = "legacy") -> bool:
        with self._connect() as conn:
            row = conn.execute(
                "SELECT owner_id FROM agent_conversations WHERE id = ?",
                (conversation_id,),
            ).fetchone()
        return row is None or row["owner_id"] == owner_id

    def delete_conversation(
        self,
        conversation_id: str,
        system: str | None = None,
        owner_id: str = "legacy",
    ) -> bool:
        with self._connect() as conn:
            if system:
                row = conn.execute(
                    "SELECT id FROM agent_conversations WHERE id = ? AND owner_id = ? AND system = ?",
                    (conversation_id, owner_id, system),
                ).fetchone()
            else:
                row = conn.execute(
                    "SELECT id FROM agent_conversations WHERE id = ? AND owner_id = ?",
                    (conversation_id, owner_id),
                ).fetchone()
            if row is None:
                return False
            conn.execute("DELETE FROM agent_messages WHERE conversation_id = ?", (conversation_id,))
            conn.execute("DELETE FROM agent_conversations WHERE id = ?", (conversation_id,))
        return True

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.database_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON")
        return conn

    def _init_db(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS agent_conversations (
                    id TEXT PRIMARY KEY,
                    owner_id TEXT NOT NULL DEFAULT 'legacy',
                    system TEXT NOT NULL,
                    title TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    context_snapshot TEXT NOT NULL DEFAULT '{}'
                )
                """
            )
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS agent_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    conversation_id TEXT NOT NULL,
                    system TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    tool_calls TEXT NOT NULL DEFAULT '[]',
                    charts TEXT NOT NULL DEFAULT '[]',
                    sources TEXT NOT NULL DEFAULT '[]',
                    questions TEXT NOT NULL DEFAULT '[]',
                    workspace TEXT NOT NULL DEFAULT '{}',
                    reports TEXT NOT NULL DEFAULT '[]',
                    FOREIGN KEY(conversation_id) REFERENCES agent_conversations(id) ON DELETE CASCADE
                )
                """
            )
            conversation_columns = {
                row["name"]
                for row in conn.execute("PRAGMA table_info(agent_conversations)").fetchall()
            }
            if "context_snapshot" not in conversation_columns:
                conn.execute("ALTER TABLE agent_conversations ADD COLUMN context_snapshot TEXT NOT NULL DEFAULT '{}'")
            if "owner_id" not in conversation_columns:
                conn.execute("ALTER TABLE agent_conversations ADD COLUMN owner_id TEXT NOT NULL DEFAULT 'legacy'")
            message_columns = {
                row["name"]
                for row in conn.execute("PRAGMA table_info(agent_messages)").fetchall()
            }
            if "questions" not in message_columns:
                conn.execute("ALTER TABLE agent_messages ADD COLUMN questions TEXT NOT NULL DEFAULT '[]'")
            if "sources" not in message_columns:
                conn.execute("ALTER TABLE agent_messages ADD COLUMN sources TEXT NOT NULL DEFAULT '[]'")
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_agent_conversations_system_updated ON agent_conversations(system, updated_at)"
            )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_agent_conversations_owner_system_updated ON agent_conversations(owner_id, system, updated_at)"
            )
            conn.execute(
                "CREATE INDEX IF NOT EXISTS idx_agent_messages_conversation ON agent_messages(conversation_id, id)"
            )

    def _remember_turn(
        self,
        conversation_id: str,
        message: str,
        answer: str,
        system: str,
        owner_id: str,
        created_at: str,
    ) -> None:
        if self._vector_store is None:
            return
        self._vector_store.remember(
            message,
            {
                "conversationId": conversation_id,
                "system": system,
                "memoryNamespace": f"{owner_id}:{system}:{conversation_id}",
                "ownerId": owner_id,
                "role": "user",
                "createdAt": created_at,
            },
        )
        self._vector_store.remember(
            answer,
            {
                "conversationId": conversation_id,
                "system": system,
                "memoryNamespace": f"{owner_id}:{system}:{conversation_id}",
                "ownerId": owner_id,
                "role": "assistant",
                "createdAt": created_at,
            },
        )


def _json_dumps(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def _json_loads(value: str, fallback: Any) -> Any:
    try:
        return json.loads(value or "")
    except (TypeError, ValueError):
        return fallback


def _message_from_row(row: sqlite3.Row) -> dict[str, Any]:
    message = {
        "role": row["role"],
        "content": row["content"],
        "createdAt": row["created_at"],
    }
    if row["role"] == "assistant":
        message.update({
            "toolCalls": _json_loads(row["tool_calls"], []),
            "charts": _json_loads(row["charts"], []),
            "sources": _json_loads(row["sources"], []),
            "questions": _json_loads(row["questions"], []),
            "workspace": _json_loads(row["workspace"], {}),
            "reports": _json_loads(row["reports"], []),
        })
    return message

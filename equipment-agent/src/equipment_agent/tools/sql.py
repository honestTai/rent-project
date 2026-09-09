from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal
from typing import Any

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.engine import Engine


MUTATION_SQL_PATTERN = re.compile(
    r"\b("
    r"insert|update|delete|replace|merge|drop|alter|truncate|create|rename|"
    r"grant|revoke|call|execute|lock|unlock|load|outfile|infile|set"
    r")\b",
    re.IGNORECASE,
)

SENSITIVE_SQL_IDENTIFIERS = {
    "address",
    "addr",
    "api_key",
    "apikey",
    "auth_no",
    "cookie",
    "contract_pdf_path",
    "contract_pdf_url",
    "id_card",
    "idcard",
    "id_card_front",
    "id_card_portrait",
    "mobile",
    "password",
    "phone",
    "private_key",
    "privatekey",
    "secret",
    "sign",
    "signature",
    "tel",
    "token",
    "user_phone",
}


@dataclass(frozen=True)
class SqlPolicyDecision:
    allowed: bool
    reason: str


class ReadOnlySqlPolicy:
    """Small SQL guard for agent-owned read-only templates."""

    def check(self, sql: str) -> SqlPolicyDecision:
        normalized = self._normalize(sql)
        if not normalized:
            return SqlPolicyDecision(False, "SQL 为空")
        if ";" in normalized[:-1]:
            return SqlPolicyDecision(False, "只读 SQL 不允许包含多语句")
        statement = normalized.rstrip(";").lstrip()
        if not re.match(r"^(select|with)\b", statement, re.IGNORECASE):
            return SqlPolicyDecision(False, "只允许执行 SELECT/WITH 只读语句")
        if MUTATION_SQL_PATTERN.search(statement):
            return SqlPolicyDecision(False, "只读策略阻止执行疑似变更 SQL")
        sensitive_identifier = self._find_sensitive_identifier(statement)
        if sensitive_identifier:
            return SqlPolicyDecision(False, f"只读策略阻止查询敏感字段 {sensitive_identifier}")
        return SqlPolicyDecision(True, "allowed read-only sql")

    def _normalize(self, sql: str) -> str:
        sql = re.sub(r"/\*.*?\*/", " ", sql or "", flags=re.DOTALL)
        sql = re.sub(r"--[^\n\r]*", " ", sql)
        return " ".join(sql.strip().split())

    def _find_sensitive_identifier(self, sql: str) -> str:
        identifiers = re.findall(r"`([^`]+)`|\b([A-Za-z_][A-Za-z0-9_]*)\b", sql)
        for quoted, bare in identifiers:
            identifier = (quoted or bare or "").lower()
            compact = identifier.replace("_", "")
            if identifier in SENSITIVE_SQL_IDENTIFIERS or compact in SENSITIVE_SQL_IDENTIFIERS:
                return identifier
        return ""


class ReadOnlySqlClient:
    def __init__(self, database_url: str, max_rows: int = 50, engine: Engine | None = None) -> None:
        self.database_url = database_url
        self.max_rows = max(1, min(int(max_rows or 50), 200))
        self.engine = engine or create_engine(database_url, pool_pre_ping=True, pool_recycle=1800)
        self.policy = ReadOnlySqlPolicy()

    def list_table_names(self) -> list[str]:
        inspector = inspect(self.engine)
        return sorted(inspector.get_table_names())

    def describe_tables(self, table_names: list[str]) -> dict[str, Any]:
        inspector = inspect(self.engine)
        available = set(inspector.get_table_names())
        tables: list[dict[str, Any]] = []
        for table_name in table_names:
            if table_name not in available:
                tables.append({"name": table_name, "exists": False, "columns": []})
                continue
            columns = []
            for column in inspector.get_columns(table_name):
                columns.append({
                    "name": column.get("name"),
                    "type": str(column.get("type")),
                    "nullable": bool(column.get("nullable", True)),
                })
            tables.append({"name": table_name, "exists": True, "columns": columns})
        return {"tables": tables}

    def query(self, sql: str, params: dict[str, Any] | None = None, max_rows: int | None = None) -> dict[str, Any]:
        decision = self.policy.check(sql)
        if not decision.allowed:
            raise ValueError(decision.reason)
        limit = max(1, min(int(max_rows or self.max_rows), 200))
        executable_sql = self._with_limit(sql, limit + 1)
        with self.engine.connect() as connection:
            result = connection.execute(text(executable_sql), params or {})
            rows = [dict(row._mapping) for row in result.fetchmany(limit + 1)]
        truncated = len(rows) > limit
        rows = rows[:limit]
        return {
            "rows": [_mask_row(_json_row(row)) for row in rows],
            "rowCount": len(rows),
            "truncated": truncated,
        }

    def _with_limit(self, sql: str, limit: int) -> str:
        statement = (sql or "").strip().rstrip(";")
        if re.search(r"\blimit\b", statement, re.IGNORECASE):
            return statement
        return f"{statement} LIMIT {int(limit)}"


def _json_row(row: dict[str, Any]) -> dict[str, Any]:
    return {key: _json_value(value) for key, value in row.items()}


def _json_value(value: Any) -> Any:
    if isinstance(value, Decimal):
        return float(value)
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if isinstance(value, bytes):
        return value.hex()[:64]
    return value


def _mask_row(row: dict[str, Any]) -> dict[str, Any]:
    return {key: ("***" if _is_sensitive_key(key) else value) for key, value in row.items()}


def _is_sensitive_key(key: str) -> bool:
    normalized = key.replace("-", "_").lower()
    compact = normalized.replace("_", "")
    return normalized in SENSITIVE_SQL_IDENTIFIERS or compact in SENSITIVE_SQL_IDENTIFIERS

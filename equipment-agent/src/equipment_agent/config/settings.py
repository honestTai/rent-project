from __future__ import annotations

import os
import re
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    agent_port: int = 7790
    platform_base_url: str = ""
    gateway_base_url: str = ""
    sqlite_path: str = "agent-memory.sqlite3"
    db_urls: dict[str, str] | None = None
    sql_max_rows: int = 50
    web_search_domains: list[str] | None = None
    web_mcp_url: str = ""
    web_mcp_tool: str = ""
    web_mcp_auth_token: str = ""
    web_mcp_timeout: float = 20.0
    builtin_web_mcp_url: str = "http://127.0.0.1:7790/mcp/"
    builtin_web_search_endpoints: list[str] | None = None
    builtin_web_search_timeout: float = 8.0
    builtin_web_mcp_allowed_hosts: list[str] | None = None
    builtin_web_mcp_allowed_origins: list[str] | None = None
    docker_log_containers: dict[str, list[str]] | None = None
    docker_log_tail: int = 200
    docker_log_since: str = "30m"


def load_settings() -> Settings:
    _load_env_files()
    agent_port = _int_env("AGENT_PORT", 7790)
    db_urls = {
        "alipay": os.getenv("AGENT_DB_ALIPAY_URL", "").strip(),
        "rental": os.getenv("AGENT_DB_RENTAL_URL", "").strip(),
        "secondhand": os.getenv("AGENT_DB_SECONDHAND_URL", "").strip(),
    }
    db_urls = {key: value for key, value in db_urls.items() if value}
    return Settings(
        agent_port=agent_port,
        platform_base_url=os.getenv("AGENT_PLATFORM_BASE_URL", "").strip(),
        gateway_base_url=os.getenv("AGENT_GATEWAY_BASE_URL", "").strip(),
        sqlite_path=os.getenv("AGENT_SQLITE_PATH", "agent-memory.sqlite3").strip() or "agent-memory.sqlite3",
        db_urls=db_urls,
        sql_max_rows=_int_env("AGENT_SQL_MAX_ROWS", 50),
        web_search_domains=_list_env("AGENT_WEB_MCP_DOMAINS") or _list_env("AGENT_WEB_SEARCH_DOMAINS"),
        web_mcp_url=os.getenv("AGENT_WEB_MCP_URL", "").strip(),
        web_mcp_tool=os.getenv("AGENT_WEB_MCP_TOOL", "").strip(),
        web_mcp_auth_token=os.getenv("AGENT_WEB_MCP_AUTH_TOKEN", "").strip(),
        web_mcp_timeout=_float_env("AGENT_WEB_MCP_TIMEOUT", 20.0),
        builtin_web_mcp_url=(
            os.getenv("AGENT_BUILTIN_WEB_MCP_URL", "").strip()
            or f"http://127.0.0.1:{agent_port}/mcp/"
        ),
        builtin_web_search_endpoints=_raw_list_env("AGENT_BUILTIN_WEB_SEARCH_ENDPOINTS"),
        builtin_web_search_timeout=_float_env("AGENT_BUILTIN_WEB_SEARCH_TIMEOUT", 8.0),
        builtin_web_mcp_allowed_hosts=_raw_list_env("AGENT_BUILTIN_WEB_MCP_ALLOWED_HOSTS"),
        builtin_web_mcp_allowed_origins=_raw_list_env("AGENT_BUILTIN_WEB_MCP_ALLOWED_ORIGINS"),
        docker_log_containers=_mapping_list_env("AGENT_DOCKER_LOG_CONTAINERS"),
        docker_log_tail=_int_env("AGENT_DOCKER_LOG_TAIL", 200),
        docker_log_since=os.getenv("AGENT_DOCKER_LOG_SINCE", "30m").strip() or "30m",
    )


def _int_env(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)) or default)
    except ValueError:
        return default


def _float_env(name: str, default: float) -> float:
    try:
        return float(os.getenv(name, str(default)) or default)
    except ValueError:
        return default


def _list_env(name: str) -> list[str] | None:
    value = os.getenv(name, "").strip()
    if not value:
        return None
    items = [item.strip().lower() for item in value.split(",") if item.strip()]
    return items or None


def _raw_list_env(name: str) -> list[str] | None:
    value = os.getenv(name, "").strip()
    if not value:
        return None
    items = [item.strip() for item in value.split(",") if item.strip()]
    return items or None


def _mapping_list_env(name: str) -> dict[str, list[str]] | None:
    value = os.getenv(name, "").strip()
    if not value:
        return None
    result: dict[str, list[str]] = {}
    for chunk in value.split(";"):
        if "=" not in chunk:
            continue
        key, raw_items = chunk.split("=", 1)
        items = [item.strip() for item in re.split(r"[,|]", raw_items) if item.strip()]
        if key.strip() and items:
            result[key.strip().lower()] = items
    return result or None


def _load_env_files() -> None:
    candidate_paths = [
        Path.cwd() / ".env",
        Path(__file__).resolve().parents[3] / ".env",
    ]
    for path in candidate_paths:
        if path.exists():
            _load_env_file(path)


def _load_env_file(path: Path) -> None:
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value

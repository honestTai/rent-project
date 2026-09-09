from __future__ import annotations

import json
import re
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Any, AsyncContextManager, Callable
from urllib.parse import urlparse
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

import httpx


DEFAULT_WEB_SEARCH_DOMAINS = [
    "opendocs.alipay.com",
    "docs.open.alipay.com",
    "open.alipay.com",
    "open.douyin.com",
    "developer.open-douyin.com",
]

QUERY_ARGUMENT_NAMES = ("query", "q", "search_query", "searchQuery", "keyword", "text")
LIMIT_ARGUMENT_NAMES = ("limit", "count", "max_results", "maxResults", "num_results", "numResults")
DOMAIN_ARGUMENT_NAMES = ("domains", "include_domains", "includeDomains", "allowed_domains", "allowedDomains")
SEARCH_TOOL_NAMES = (
    "web_search",
    "search_web",
    "search",
    "brave_web_search",
    "tavily_search",
)


def sanitize_search_query(query: str) -> str:
    text = (query or "").strip()
    text = re.sub(
        r"(你联网查询一下|你联网查询|你联网查一下|你联网查|你网上搜索|你网上查|你帮我查|"
        r"联网查询一下|联网查询|联网查一下|联网搜索一下|联网搜索|联网查|网上搜索|网上查|搜索一下|查一下|搜一下|帮我查|"
        r"然后总结给我|总结给我|给我一句摘要|给我摘要|我说的是|我说得是|说的是|说得是)",
        " ",
        text,
    )
    text = re.sub(r"^[\s,，。:：;；、!?！？]+", "", text)
    text = re.sub(r"\b1[3-9]\d{9}\b", "[手机号]", text)
    text = re.sub(r"\b[A-Za-z0-9][A-Za-z0-9_-]{15,}\b", "[业务编号]", text)
    return " ".join(text.split())[:160]


class McpWebSearchClient:
    """Use one remote MCP server as the only public-internet search exit.

    The Agent keeps a stable internal ``search_web`` capability while this
    adapter discovers and calls the configured MCP search tool. There is no
    direct search-engine or page-fetching fallback in this client.
    """

    def __init__(
        self,
        endpoint: str = "",
        *,
        tool_name: str = "",
        auth_token: str = "",
        timeout: float = 20.0,
        session_factory: Callable[[], AsyncContextManager[Any]] | None = None,
        clock: Callable[[], datetime] | None = None,
    ) -> None:
        self.endpoint = (endpoint or "").strip()
        self.tool_name = (tool_name or "").strip()
        self.auth_token = (auth_token or "").strip()
        self.timeout = max(1.0, float(timeout or 20.0))
        self.session_factory = session_factory
        self.clock = clock or (lambda: datetime.now(timezone.utc))

    async def search(
        self,
        query: str,
        *,
        limit: int = 5,
        domains: list[str] | None = None,
    ) -> list[dict[str, Any]]:
        safe_query = sanitize_search_query(query)
        if not safe_query:
            return []
        if not self.endpoint and self.session_factory is None:
            raise RuntimeError("未配置联网 MCP 地址 AGENT_WEB_MCP_URL")

        safe_limit = max(1, min(int(limit or 5), 10))
        safe_domains = [
            str(domain).strip().lower()
            for domain in (domains or [])
            if str(domain).strip()
        ][:8]
        async with self._open_session() as session:
            response = await session.list_tools()
            tools = list(getattr(response, "tools", None) or [])
            tool = self._select_search_tool(tools)
            arguments = self._build_arguments(tool, safe_query, safe_limit, safe_domains)
            result = await session.call_tool(self._tool_value(tool, "name"), arguments=arguments)

        if bool(self._result_value(result, "isError", "is_error")):
            message = self._result_text(result) or "联网 MCP 工具调用失败"
            raise RuntimeError(message[:500])
        rows = self._normalize_result(result, 10 if safe_domains else safe_limit)
        if safe_domains:
            rows = [row for row in rows if self._domain_allowed(str(row.get("url") or ""), safe_domains)]
        return rows[:safe_limit]

    async def current_time(self, timezone_name: str = "Asia/Shanghai") -> dict[str, Any]:
        timezone_value = str(timezone_name or "Asia/Shanghai").strip() or "Asia/Shanghai"
        if not self.endpoint and self.session_factory is None:
            raise RuntimeError("未配置联网 MCP 地址 AGENT_WEB_MCP_URL")

        async with self._open_session() as session:
            response = await session.list_tools()
            tools = list(getattr(response, "tools", None) or [])
            tool = next(
                (item for item in tools if self._tool_value(item, "name") == "current_time"),
                None,
            )
            if tool is None:
                available = ", ".join(self._tool_value(item, "name") for item in tools[:12]) or "无"
                raise RuntimeError(f"联网 MCP 未提供工具 current_time；当前工具：{available}")
            result = await session.call_tool("current_time", arguments={"timezone": timezone_value})

        if bool(self._result_value(result, "isError", "is_error")):
            message = self._result_text(result) or "联网 MCP 时间工具调用失败"
            raise RuntimeError(message[:500])
        structured = self._result_value(result, "structuredContent", "structured_content")
        if isinstance(structured, dict):
            return self._normalize_current_time(structured, timezone_value)
        for text in self._content_texts(result):
            try:
                payload = json.loads(text)
            except (TypeError, ValueError, json.JSONDecodeError):
                continue
            if isinstance(payload, dict):
                return self._normalize_current_time(payload, timezone_value)
        raise RuntimeError("联网 MCP 时间工具未返回结构化结果")

    def _normalize_current_time(self, payload: dict[str, Any], expected_timezone: str) -> dict[str, Any]:
        timezone_name = str(payload.get("timezone") or "").strip()
        if timezone_name != expected_timezone or not re.fullmatch(r"[A-Za-z0-9_+./-]{1,64}", timezone_name):
            raise RuntimeError("联网 MCP 时间工具返回的时区与请求不一致")
        iso_text = str(payload.get("iso") or "").strip()
        if not 10 <= len(iso_text) <= 64:
            raise RuntimeError("联网 MCP 时间工具未返回有效 ISO 时间")
        try:
            parsed = datetime.fromisoformat(iso_text.replace("Z", "+00:00"))
            zone = ZoneInfo(timezone_name)
        except (ValueError, ZoneInfoNotFoundError) as exc:
            raise RuntimeError("联网 MCP 时间工具返回了无效的时区化时间") from exc
        if parsed.tzinfo is None or parsed.utcoffset() is None:
            raise RuntimeError("联网 MCP 时间工具返回的 ISO 时间缺少 UTC 偏移")

        observed_at = self.clock()
        if observed_at.tzinfo is None or observed_at.utcoffset() is None:
            observed_at = observed_at.replace(tzinfo=timezone.utc)
        skew_seconds = abs((parsed.astimezone(timezone.utc) - observed_at.astimezone(timezone.utc)).total_seconds())
        if skew_seconds > 300:
            raise RuntimeError("联网 MCP 时间工具返回的时间已过期")

        local_time = parsed.astimezone(zone).replace(microsecond=0)
        offset = local_time.strftime("%z")
        utc_offset = f"{offset[:3]}:{offset[3:]}" if len(offset) == 5 else offset
        weekdays = ("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        return {
            "timezone": timezone_name,
            "utcOffset": utc_offset,
            "iso": local_time.isoformat(timespec="seconds"),
            "date": local_time.strftime("%Y年%m月%d日"),
            "time": local_time.strftime("%H:%M:%S"),
            "weekday": weekdays[local_time.weekday()],
            "display": f"{local_time.strftime('%Y年%m月%d日 %H:%M:%S')}（{weekdays[local_time.weekday()]}）",
        }

    @asynccontextmanager
    async def _open_session(self):
        if self.session_factory is not None:
            async with self.session_factory() as session:
                yield session
            return

        # Imports stay local so unit tests can inject an in-memory session
        # without requiring transport initialization.
        from mcp import ClientSession
        from mcp.client.streamable_http import streamable_http_client

        headers = {"Authorization": f"Bearer {self.auth_token}"} if self.auth_token else {}
        async with httpx.AsyncClient(
            headers=headers,
            timeout=httpx.Timeout(self.timeout),
            follow_redirects=True,
        ) as http_client:
            async with streamable_http_client(self.endpoint, http_client=http_client) as streams:
                async with ClientSession(streams[0], streams[1]) as session:
                    await session.initialize()
                    yield session

    def _select_search_tool(self, tools: list[Any]) -> Any:
        if self.tool_name:
            selected = next(
                (tool for tool in tools if self._tool_value(tool, "name") == self.tool_name),
                None,
            )
            if selected is None:
                available = ", ".join(self._tool_value(tool, "name") for tool in tools[:12]) or "无"
                raise RuntimeError(f"联网 MCP 未提供工具 {self.tool_name}；当前工具：{available}")
            return selected

        ranked = sorted(
            ((self._search_tool_score(tool), index, tool) for index, tool in enumerate(tools)),
            key=lambda item: (-item[0], item[1]),
        )
        if not ranked or ranked[0][0] <= 0:
            available = ", ".join(self._tool_value(tool, "name") for tool in tools[:12]) or "无"
            raise RuntimeError(f"联网 MCP 没有可识别的网页搜索工具；当前工具：{available}")
        return ranked[0][2]

    def _search_tool_score(self, tool: Any) -> int:
        name = self._tool_value(tool, "name").strip().lower()
        description = self._tool_value(tool, "description").strip().lower()
        if name in SEARCH_TOOL_NAMES:
            return 100 - SEARCH_TOOL_NAMES.index(name)
        score = 0
        if "search" in name or "搜索" in name:
            score += 30
        if "web" in name or "internet" in name or "网页" in name or "联网" in name:
            score += 20
        if "search" in description or "搜索" in description or "检索" in description:
            score += 10
        if "web" in description or "internet" in description or "网页" in description:
            score += 5
        return score

    def _build_arguments(
        self,
        tool: Any,
        query: str,
        limit: int,
        domains: list[str],
    ) -> dict[str, Any]:
        schema = self._tool_schema(tool)
        properties = schema.get("properties") if isinstance(schema, dict) else {}
        properties = properties if isinstance(properties, dict) else {}

        query_key = next((name for name in QUERY_ARGUMENT_NAMES if name in properties), "query")
        domain_key = next((name for name in DOMAIN_ARGUMENT_NAMES if name in properties), "")
        constrained_query = query
        if domains and not domain_key:
            constrained_query = f"{query} ({' OR '.join(f'site:{domain}' for domain in domains)})"
        arguments: dict[str, Any] = {query_key: constrained_query}
        limit_key = next((name for name in LIMIT_ARGUMENT_NAMES if name in properties), "")
        if limit_key:
            arguments[limit_key] = limit
        if domain_key and domains:
            domain_schema = properties.get(domain_key)
            expects_string = isinstance(domain_schema, dict) and domain_schema.get("type") == "string"
            arguments[domain_key] = ",".join(domains) if expects_string else domains
        return arguments

    def _normalize_result(self, result: Any, limit: int) -> list[dict[str, Any]]:
        payloads: list[Any] = []
        structured = self._result_value(result, "structuredContent", "structured_content")
        if structured is not None:
            payloads.append(structured)
        for text in self._content_texts(result):
            try:
                payloads.append(json.loads(text))
            except (TypeError, ValueError, json.JSONDecodeError):
                payloads.append(text)

        rows: list[dict[str, Any]] = []
        for payload in payloads:
            rows.extend(self._rows_from_payload(payload))

        normalized: list[dict[str, Any]] = []
        seen: set[str] = set()
        for row in rows:
            item = self._normalize_row(row)
            if item is None:
                continue
            identity = item.get("url") or f"{item.get('title')}|{item.get('snippet')}"
            if identity in seen:
                continue
            seen.add(identity)
            normalized.append(item)
            if len(normalized) >= limit:
                break
        return normalized

    def _rows_from_payload(self, payload: Any) -> list[Any]:
        if isinstance(payload, list):
            rows: list[Any] = []
            for item in payload:
                rows.extend(self._rows_from_payload(item))
            return rows
        if isinstance(payload, str):
            return self._rows_from_text(payload)
        if not isinstance(payload, dict):
            return []
        if self._looks_like_result_row(payload):
            return [payload]

        rows = []
        for key in (
            "results",
            "result",
            "items",
            "data",
            "documents",
            "webPages",
            "web_pages",
            "organic",
            "hits",
            "sources",
            "citations",
        ):
            value = payload.get(key)
            if value is not None:
                rows.extend(self._rows_from_payload(value))
        for key in ("answer", "summary", "content", "text", "message"):
            value = payload.get(key)
            if isinstance(value, str) and value.strip():
                rows.append({"title": "联网 MCP 返回", "snippet": value.strip()})
                break
        return rows

    def _rows_from_text(self, text: str) -> list[dict[str, Any]]:
        clean = str(text or "").strip()
        if not clean:
            return []
        rows = []
        for title, url in re.findall(r"\[([^\]]+)\]\((https?://[^)\s]+)\)", clean):
            rows.append({"title": title.strip(), "url": url.strip(), "snippet": clean[:1600]})
        if rows:
            return rows
        urls = list(dict.fromkeys(re.findall(r"https?://[^\s<>\])]+", clean)))
        if urls:
            return [
                {"title": "联网 MCP 返回", "url": url, "snippet": clean[:1600]}
                for url in urls
            ]
        return [{"title": "联网 MCP 返回", "snippet": clean[:1600]}]

    def _normalize_row(self, row: Any) -> dict[str, Any] | None:
        if isinstance(row, str):
            row = {"snippet": row}
        if not isinstance(row, dict):
            return None
        title = self._first_text(row, "title", "name", "headline") or "联网 MCP 返回"
        url = self._first_text(row, "url", "link", "href", "sourceUrl", "source_url")
        if url and urlparse(url).scheme.lower() not in {"http", "https"}:
            url = ""
        snippet = self._first_text(row, "snippet", "description", "summary", "content", "text", "answer")
        if not url and not snippet and title == "联网 MCP 返回":
            return None
        source = self._first_text(row, "source", "domain", "site")
        if not source and url:
            source = urlparse(url).netloc.lower()
        return {
            "title": title[:200],
            "url": url[:1000],
            "snippet": snippet[:1600],
            "source": source[:160],
        }

    def _looks_like_result_row(self, value: dict[str, Any]) -> bool:
        keys = set(value)
        has_url = bool(keys.intersection({"url", "link", "href", "sourceUrl", "source_url"}))
        has_text = bool(keys.intersection({"title", "name", "headline", "snippet", "description", "content", "text"}))
        return has_url and has_text

    def _content_texts(self, result: Any) -> list[str]:
        content = self._result_value(result, "content") or []
        texts: list[str] = []
        for item in content if isinstance(content, list) else []:
            if isinstance(item, dict):
                text = item.get("text") if item.get("type") in {None, "text"} else None
            else:
                text = getattr(item, "text", None)
            if text is not None and str(text).strip():
                texts.append(str(text).strip())
        return texts

    def _result_text(self, result: Any) -> str:
        return "\n".join(self._content_texts(result)).strip()

    def _tool_schema(self, tool: Any) -> dict[str, Any]:
        if isinstance(tool, dict):
            value = tool.get("inputSchema") or tool.get("input_schema")
        else:
            value = getattr(tool, "inputSchema", None) or getattr(tool, "input_schema", None)
        return value if isinstance(value, dict) else {}

    def _tool_value(self, tool: Any, name: str) -> str:
        value = tool.get(name) if isinstance(tool, dict) else getattr(tool, name, "")
        return str(value or "")

    def _result_value(self, result: Any, *names: str) -> Any:
        for name in names:
            if isinstance(result, dict) and name in result:
                return result[name]
            if not isinstance(result, dict) and hasattr(result, name):
                return getattr(result, name)
        return None

    def _first_text(self, value: dict[str, Any], *names: str) -> str:
        for name in names:
            item = value.get(name)
            if item is not None and str(item).strip():
                return str(item).strip()
        return ""

    def _domain_allowed(self, url: str, domains: list[str]) -> bool:
        host = urlparse(url).netloc.lower()
        return bool(host) and any(host == domain or host.endswith(f".{domain}") for domain in domains)

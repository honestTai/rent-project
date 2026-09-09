from __future__ import annotations

from datetime import datetime
from typing import Any, Callable, Protocol
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from mcp.server.fastmcp import FastMCP
from mcp.server.transport_security import TransportSecuritySettings

from equipment_agent.tools.web_search import sanitize_search_query


class WebSearchProvider(Protocol):
    async def search(
        self,
        query: str,
        *,
        limit: int = 5,
        domains: list[str] | None = None,
    ) -> list[dict[str, Any]]: ...


def create_web_mcp_server(
    provider: WebSearchProvider,
    *,
    allowed_hosts: list[str] | None = None,
    allowed_origins: list[str] | None = None,
    clock: Callable[[ZoneInfo], datetime] | None = None,
) -> FastMCP:
    local_hosts = allowed_hosts or ["127.0.0.1", "127.0.0.1:*", "localhost", "localhost:*"]
    server = FastMCP(
        "Equipment Agent Built-in Web MCP",
        instructions=(
            "Read-only public web search for the Equipment Agent. "
            "Never send internal order data, credentials, logs, or personal information."
        ),
        streamable_http_path="/",
        stateless_http=True,
        json_response=True,
        transport_security=TransportSecuritySettings(
            enable_dns_rebinding_protection=True,
            allowed_hosts=local_hosts,
            allowed_origins=allowed_origins or [],
        ),
    )

    @server.tool(
        name="web_search",
        title="联网检索公开资料",
        description="检索公开网页、官方文档、实时天气、新闻、政策和公开规则。只读。",
        structured_output=True,
    )
    async def web_search(
        query: str,
        limit: int = 5,
        domains: list[str] | None = None,
    ) -> dict[str, Any]:
        safe_query = sanitize_search_query(query)
        if not safe_query:
            return {"query": "", "count": 0, "results": []}
        safe_limit = max(1, min(int(limit or 5), 10))
        safe_domains = [
            str(domain).strip().lower()
            for domain in (domains or [])
            if str(domain).strip()
        ][:8]
        rows = await provider.search(
            safe_query,
            limit=safe_limit,
            domains=safe_domains,
        )
        return {
            "query": safe_query,
            "count": len(rows),
            "results": rows,
        }

    @server.tool(
        name="current_time",
        title="查询当前时间",
        description="返回指定 IANA 时区的当前日期、时间、星期和 UTC 偏移。只读。",
        structured_output=True,
    )
    async def current_time(timezone: str = "Asia/Shanghai") -> dict[str, Any]:
        timezone_name = str(timezone or "Asia/Shanghai").strip() or "Asia/Shanghai"
        try:
            zone = ZoneInfo(timezone_name)
        except ZoneInfoNotFoundError as exc:
            raise ValueError(f"不支持的时区：{timezone_name}") from exc
        now = (clock or datetime.now)(zone)
        if now.tzinfo is None:
            now = now.replace(tzinfo=zone)
        else:
            now = now.astimezone(zone)
        offset = now.strftime("%z")
        utc_offset = f"{offset[:3]}:{offset[3:]}" if len(offset) == 5 else offset
        weekdays = ("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        return {
            "timezone": timezone_name,
            "utcOffset": utc_offset,
            "iso": now.isoformat(timespec="seconds"),
            "date": now.strftime("%Y年%m月%d日"),
            "time": now.strftime("%H:%M:%S"),
            "weekday": weekdays[now.weekday()],
            "display": f"{now.strftime('%Y年%m月%d日 %H:%M:%S')}（{weekdays[now.weekday()]}）",
        }

    return server

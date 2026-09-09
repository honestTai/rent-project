from __future__ import annotations

import base64
import httpx
import pytest
from mcp import ClientSession
from mcp.client.streamable_http import streamable_http_client

from equipment_agent.agent.workflow import AgentWorkflow
from equipment_agent.api.app import create_app
from equipment_agent.mcp.search_provider import PublicWebSearchProvider
from equipment_agent.memory.store import MemoryStore


class FakePublicSearchProvider:
    def __init__(self) -> None:
        self.requests = []

    async def search(self, query, *, limit=5, domains=None):
        self.requests.append((query, limit, domains))
        return [{
            "title": "支付宝开放平台文档",
            "url": "https://opendocs.alipay.com/open/demo",
            "snippet": "介绍租赁组件接入条件。",
            "source": "opendocs.alipay.com",
        }]


@pytest.mark.asyncio
async def test_builtin_mcp_exposes_search_tool_through_streamable_http():
    provider = FakePublicSearchProvider()
    app = create_app(
        memory_store=MemoryStore(),
        workflow=AgentWorkflow(),
        web_search_provider=provider,
    )

    async with app.router.lifespan_context(app):
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://127.0.0.1") as http_client:
            health = await http_client.get("/health")
            async with streamable_http_client(
                "http://127.0.0.1/mcp/",
                http_client=http_client,
            ) as streams:
                async with ClientSession(streams[0], streams[1]) as session:
                    await session.initialize()
                    tools = await session.list_tools()
                    result = await session.call_tool(
                        "web_search",
                        {
                            "query": "联网查一下 13812345678 支付宝租赁接入规则",
                            "limit": 3,
                            "domains": ["opendocs.alipay.com"],
                        },
                    )
                    current_time = await session.call_tool(
                        "current_time",
                        {"timezone": "Asia/Shanghai"},
                    )

    assert [tool.name for tool in tools.tools] == ["web_search", "current_time"]
    assert health.json() == {"status": "UP", "webMcp": "builtin"}
    assert provider.requests == [
        ("[手机号] 支付宝租赁接入规则", 3, ["opendocs.alipay.com"]),
    ]
    assert result.isError is False
    assert result.structuredContent["results"][0]["title"] == "支付宝开放平台文档"
    assert current_time.isError is False
    assert current_time.structuredContent["timezone"] == "Asia/Shanghai"
    assert current_time.structuredContent["utcOffset"] == "+08:00"


def test_builtin_provider_parses_and_enforces_domain_allowlist():
    html = """
    <html><body>
      <li class="b_algo">
        <h2><a href="https://example.com/copied">转载规则</a></h2>
        <div class="b_caption"><p>非官方转载。</p></div>
      </li>
      <li class="b_algo">
        <h2><a href="https://opendocs.alipay.com/open/demo">支付宝官方规则</a></h2>
        <div class="b_caption"><p>官方接入条件。</p></div>
      </li>
    </body></html>
    """
    provider = PublicWebSearchProvider()

    rows = provider._parse_results(
        html,
        limit=5,
        domains=["opendocs.alipay.com"],
    )

    assert len(rows) == 1
    assert rows[0]["title"] == "支付宝官方规则"
    assert rows[0]["source"] == "opendocs.alipay.com"


def test_builtin_provider_parses_bing_rss_and_enforces_domain_allowlist():
    rss = """<?xml version="1.0" encoding="utf-8" ?>
    <rss version="2.0"><channel>
      <item>
        <title>转载文档</title>
        <link>https://example.com/copied</link>
        <description>非官方内容。</description>
      </item>
      <item>
        <title>支付宝开放平台文档</title>
        <link>https://opendocs.alipay.com/open/demo</link>
        <description><![CDATA[<b>官方</b>接入条件。]]></description>
      </item>
    </channel></rss>
    """
    provider = PublicWebSearchProvider()

    rows = provider._parse_results(
        rss,
        limit=5,
        domains=["opendocs.alipay.com"],
    )

    assert rows == [{
        "title": "支付宝开放平台文档",
        "url": "https://opendocs.alipay.com/open/demo",
        "snippet": "官方 接入条件。",
        "source": "opendocs.alipay.com",
    }]


def test_builtin_provider_broadens_long_official_domain_queries():
    provider = PublicWebSearchProvider()

    variants = provider._query_variants(
        "https://www.bing.com/search?format=rss",
        "支付宝租赁频道 接入说明 (site:opendocs.alipay.com OR site:open.alipay.com)",
        "支付宝租赁频道 公开接入说明 接入文档 租赁小程序",
        ["opendocs.alipay.com", "open.alipay.com"],
    )

    assert "支付宝租赁频道 site:opendocs.alipay.com" in variants
    assert "支付宝租赁频道 site:open.alipay.com" in variants
    assert "支付宝租赁 site:opendocs.alipay.com" in variants
    assert "支付宝租赁 site:open.alipay.com" in variants


def test_builtin_provider_decodes_bing_redirect_before_domain_filtering():
    provider = PublicWebSearchProvider()
    target = "https://opendocs.alipay.com/open/demo"
    encoded = base64.urlsafe_b64encode(target.encode()).decode().rstrip("=")

    value = provider._normalize_url(
        f"https://www.bing.com/ck/a?u=a1{encoded}&ntb=1"
    )

    assert value == target

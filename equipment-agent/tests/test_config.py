import httpx
import pytest

from equipment_agent.config.model_config import ModelConfigClient
from equipment_agent.config.settings import load_settings


@pytest.mark.asyncio
async def test_loads_model_config_from_platform_internal_api():
    requests = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(str(request.url))
        key = request.url.params["configKey"]
        values = {
            "agent.llm.provider": "ds",
            "agent.llm.model": "deepseek-chat",
            "agent.llm.base-url": "https://api.deepseek.com",
            "agent.llm.api-key": "secret-key",
        }
        return httpx.Response(200, json={"code": 0, "data": values[key]})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://platform")
    config = await ModelConfigClient("http://platform", client=client).load()

    assert config.provider == "ds"
    assert config.model == "deepseek-chat"
    assert config.base_url == "https://api.deepseek.com"
    assert config.base_url == "https://api.deepseek.com"
    assert config.api_key == "secret-key"
    assert all("/api/platform/internal/config/value" in url for url in requests)


def test_loads_web_mcp_settings(monkeypatch):
    monkeypatch.setenv("AGENT_PORT", "7788")
    monkeypatch.setenv("AGENT_WEB_MCP_DOMAINS", "opendocs.alipay.com, open.douyin.com ")
    monkeypatch.setenv("AGENT_WEB_MCP_URL", "https://mcp.example.com/mcp")
    monkeypatch.setenv("AGENT_WEB_MCP_TOOL", "web_search")
    monkeypatch.setenv("AGENT_WEB_MCP_AUTH_TOKEN", "mcp-secret")
    monkeypatch.setenv("AGENT_WEB_MCP_TIMEOUT", "12.5")
    monkeypatch.setenv("AGENT_BUILTIN_WEB_SEARCH_ENDPOINTS", "https://search-one.example/query,https://search-two.example/query")
    monkeypatch.setenv("AGENT_BUILTIN_WEB_SEARCH_TIMEOUT", "6.5")

    settings = load_settings()

    assert settings.web_search_domains == ["opendocs.alipay.com", "open.douyin.com"]
    assert settings.web_mcp_url == "https://mcp.example.com/mcp"
    assert settings.web_mcp_tool == "web_search"
    assert settings.web_mcp_auth_token == "mcp-secret"
    assert settings.web_mcp_timeout == 12.5
    assert settings.builtin_web_mcp_url == "http://127.0.0.1:7788/mcp/"
    assert settings.builtin_web_search_endpoints == [
        "https://search-one.example/query",
        "https://search-two.example/query",
    ]
    assert settings.builtin_web_search_timeout == 6.5


def test_loads_docker_log_settings(monkeypatch):
    monkeypatch.setenv("AGENT_DOCKER_LOG_CONTAINERS", "alipay=equipment-alipay-service|gateway;secondhand=equipment-secondhand")
    monkeypatch.setenv("AGENT_DOCKER_LOG_TAIL", "300")
    monkeypatch.setenv("AGENT_DOCKER_LOG_SINCE", "2h")

    settings = load_settings()

    assert settings.docker_log_containers == {
        "alipay": ["equipment-alipay-service", "gateway"],
        "secondhand": ["equipment-secondhand"],
    }
    assert settings.docker_log_tail == 300
    assert settings.docker_log_since == "2h"


@pytest.mark.asyncio
async def test_uses_deepseek_defaults_when_platform_config_is_empty():
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"code": 0, "data": ""})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://platform")
    config = await ModelConfigClient("http://platform", client=client).load()

    assert config.provider == "ds"
    assert config.model == "deepseek-chat"

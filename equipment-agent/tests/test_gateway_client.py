from __future__ import annotations

import httpx
import pytest

from equipment_agent.tools.gateway import GatewayClient


@pytest.mark.asyncio
async def test_gateway_rejects_business_error_code():
    async def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"code": 401, "msg": "无token", "data": None})

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler), base_url="http://gateway")
    gateway = GatewayClient("http://gateway", client=client)

    with pytest.raises(ValueError, match="无token"):
        await gateway.request("POST", "/api/home/dashboard", {"periodType": "MONTH"})

    await client.aclose()

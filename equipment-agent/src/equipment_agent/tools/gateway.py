from __future__ import annotations

from typing import Any

import httpx

from equipment_agent.tools.policy import ReadOnlyPolicy, ToolRequest


class GatewayClient:
    def __init__(self, gateway_base_url: str, token: str | None = None, client: httpx.AsyncClient | None = None) -> None:
        self.gateway_base_url = gateway_base_url.rstrip("/")
        self.token = token
        self.client = client
        self.policy = ReadOnlyPolicy()

    async def request(self, method: str, path: str, payload: dict[str, Any] | None = None) -> dict[str, Any]:
        decision = self.policy.check(ToolRequest(method=method, path=path))
        if not decision.allowed:
            raise ValueError(decision.reason)
        close_client = False
        client = self.client
        if client is None:
            client = httpx.AsyncClient(base_url=self.gateway_base_url, timeout=30)
            close_client = True
        headers = {"X-Request-Source": "admin"}
        if self.token:
            headers["token"] = self.token
        try:
            if method.upper() == "GET":
                response = await client.get(path, params=payload or {}, headers=headers)
            else:
                response = await client.post(path, json=payload or {}, headers=headers)
            response.raise_for_status()
            body = response.json()
            self._raise_for_business_error(body)
            return body
        finally:
            if close_client:
                await client.aclose()

    def _raise_for_business_error(self, body: dict[str, Any]) -> None:
        if not isinstance(body, dict) or "code" not in body:
            return
        try:
            code = int(body.get("code"))
        except (TypeError, ValueError):
            raise ValueError(str(body.get("msg") or "业务接口返回异常"))
        if code not in (0, 200):
            raise ValueError(str(body.get("msg") or f"业务接口返回 code={code}"))

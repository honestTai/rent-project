from __future__ import annotations

from dataclasses import dataclass

import httpx

DEFAULT_PROVIDER = "ds"
DEFAULT_MODEL = "deepseek-chat"
DEFAULT_BASE_URL = "https://api.deepseek.com"


@dataclass(frozen=True)
class ModelConfig:
    provider: str = DEFAULT_PROVIDER
    model: str = DEFAULT_MODEL
    base_url: str = DEFAULT_BASE_URL
    api_key: str = ""


class ModelConfigClient:
    def __init__(self, platform_base_url: str, client: httpx.AsyncClient | None = None) -> None:
        self.platform_base_url = platform_base_url.rstrip("/")
        self.client = client

    async def load(self) -> ModelConfig:
        close_client = False
        client = self.client
        if client is None:
            client = httpx.AsyncClient(base_url=self.platform_base_url, timeout=10)
            close_client = True
        try:
            values = {
                key: await self._read_value(client, key)
                for key in [
                    "agent.llm.provider",
                    "agent.llm.model",
                    "agent.llm.base-url",
                    "agent.llm.api-key",
                ]
            }
        finally:
            if close_client:
                await client.aclose()
        return ModelConfig(
            provider=values["agent.llm.provider"] or DEFAULT_PROVIDER,
            model=values["agent.llm.model"] or DEFAULT_MODEL,
            base_url=values["agent.llm.base-url"] or DEFAULT_BASE_URL,
            api_key=values["agent.llm.api-key"] or "",
        )

    async def _read_value(self, client: httpx.AsyncClient, config_key: str) -> str:
        try:
            response = await client.get(
                "/api/platform/internal/config/value",
                params={"systemCode": "platform", "configKey": config_key},
            )
            response.raise_for_status()
            body = response.json()
        except Exception:
            return ""
        try:
            code = int(body.get("code", 0))
        except (TypeError, ValueError):
            return ""
        if code not in (0, 200):
            return ""
        value = body.get("data")
        return "" if value is None else str(value)

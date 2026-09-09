from __future__ import annotations

from typing import Any

from equipment_agent.config.model_config import ModelConfig

DEEPSEEK_BASE_URL = "https://api.deepseek.com"


def build_chat_model(config: ModelConfig) -> Any | None:
    if not config.api_key:
        return None
    try:
        from langchain_openai import ChatOpenAI
    except Exception:
        return None

    provider = config.provider.strip().lower()
    base_url = config.base_url.strip()
    if not base_url and provider in {"ds", "deepseek"}:
        base_url = DEEPSEEK_BASE_URL

    kwargs: dict[str, Any] = {
        "model": config.model,
        "api_key": config.api_key,
    }
    if provider in {"ds", "deepseek"}:
        kwargs["reasoning_effort"] = "high"
        kwargs["extra_body"] = {"thinking": {"type": "enabled"}}
    else:
        kwargs["temperature"] = 0.2
    if base_url:
        kwargs["base_url"] = base_url
    return ChatOpenAI(**kwargs)

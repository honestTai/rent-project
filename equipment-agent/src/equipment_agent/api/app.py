from __future__ import annotations

import asyncio
import json
from contextlib import asynccontextmanager, suppress
from typing import Any
from uuid import uuid4

from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from equipment_agent.agent.workflow import AgentWorkflow
from equipment_agent.agent.events import RunEvent
from equipment_agent.agent.run_registry import RunRegistry
from equipment_agent.config.model_config import ModelConfigClient
from equipment_agent.config.settings import load_settings
from equipment_agent.memory.store import MemoryStore, SqliteMemoryStore
from equipment_agent.memory.vector_store import VectorMemoryStore
from equipment_agent.mcp.search_provider import PublicWebSearchProvider
from equipment_agent.mcp.web_server import WebSearchProvider, create_web_mcp_server
from equipment_agent.tools.docker_logs import DockerLogsClient
from equipment_agent.tools.web_search import McpWebSearchClient

ALLOWED_SYSTEMS = {"alipay", "rental", "secondhand"}
STREAM_HEARTBEAT_SECONDS = 15.0


class ChatRequest(BaseModel):
    message: str = ""
    conversation_id: str | None = Field(default=None, alias="conversationId")
    run_id: str | None = Field(default=None, alias="runId")
    action: str | None = None
    system: str | None = "alipay"
    context: dict[str, Any] = Field(default_factory=dict)


class ConversationSummary(BaseModel):
    id: str
    title: str
    updatedAt: str


def success(data: Any) -> dict[str, Any]:
    return {"code": 0, "msg": "查询成功", "data": data}


def normalize_system(system: str | None) -> str:
    value = (system or "alipay").strip().lower()
    return value if value in ALLOWED_SYSTEMS else "alipay"


def normalize_owner_id(owner_id: str | None) -> str:
    value = (owner_id or "legacy").strip()
    return value[:128] or "legacy"


def create_app(
    memory_store: MemoryStore | None = None,
    workflow: AgentWorkflow | None = None,
    run_registry: RunRegistry | None = None,
    web_search_provider: WebSearchProvider | None = None,
) -> FastAPI:
    settings = load_settings()
    provider = web_search_provider or PublicWebSearchProvider(
        endpoints=settings.builtin_web_search_endpoints,
        timeout=settings.builtin_web_search_timeout,
    )
    web_mcp_server = create_web_mcp_server(
        provider,
        allowed_hosts=settings.builtin_web_mcp_allowed_hosts,
        allowed_origins=settings.builtin_web_mcp_allowed_origins,
    )
    web_mcp_app = web_mcp_server.streamable_http_app()

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        async with web_mcp_server.session_manager.run():
            yield

    app = FastAPI(title="Equipment Read-only Analysis Agent", lifespan=lifespan)
    store = memory_store or SqliteMemoryStore(settings.sqlite_path, vector_store=VectorMemoryStore())
    model_config_client = ModelConfigClient(settings.platform_base_url) if settings.platform_base_url else None
    gateway_base_url = settings.gateway_base_url or settings.platform_base_url
    use_remote_web_mcp = bool(settings.web_mcp_url)
    web_mcp_endpoint = settings.web_mcp_url or settings.builtin_web_mcp_url
    agent = workflow or AgentWorkflow(
        model_config_client=model_config_client,
        gateway_base_url=gateway_base_url,
        knowledge_store=store.vector_store,
        db_urls=settings.db_urls or {},
        sql_max_rows=settings.sql_max_rows,
        web_search_client=McpWebSearchClient(
            endpoint=web_mcp_endpoint,
            tool_name=settings.web_mcp_tool or ("" if use_remote_web_mcp else "web_search"),
            auth_token=settings.web_mcp_auth_token if use_remote_web_mcp else "",
            timeout=settings.web_mcp_timeout,
        ),
        web_search_domains=settings.web_search_domains,
        docker_logs_client=(
            DockerLogsClient(
                container_patterns=settings.docker_log_containers,
                default_tail=settings.docker_log_tail,
                default_since=settings.docker_log_since,
            )
            if settings.docker_log_containers
            else DockerLogsClient(
                default_tail=settings.docker_log_tail,
                default_since=settings.docker_log_since,
            )
        ),
    )
    runs = run_registry or RunRegistry()

    def build_context(payload: ChatRequest, conversation_id: str, system: str, owner_id: str) -> dict[str, Any]:
        context = dict(payload.context or {})
        if payload.conversation_id:
            if not store.can_access_conversation(conversation_id, owner_id=owner_id):
                raise HTTPException(status_code=403, detail="无权访问该会话")
            conversation = store.get_conversation(conversation_id, system=system, owner_id=owner_id)
            history = conversation.get("messages", [])
            if isinstance(history, list) and history:
                context.setdefault("conversationHistory", history[-8:])
            snapshot = conversation.get("contextSnapshot")
            if isinstance(snapshot, dict) and snapshot:
                context.setdefault("conversationContext", snapshot)
        return context

    def response_data(conversation_id: str, system: str, result: dict[str, Any]) -> dict[str, Any]:
        return {
            "conversationId": conversation_id,
            "system": result.get("system") or system,
            "answer": result["answer"],
            "toolCalls": result.get("toolCalls", []),
            "charts": result.get("charts", []),
            "sources": result.get("sources", []),
            "questions": result.get("questions", []),
            "workspace": result.get("workspace", {}),
            "contextSnapshot": result.get("contextSnapshot", {}),
            "reports": result.get("reports", []),
        }

    def sse(event: RunEvent, data: dict[str, Any] | None = None) -> str:
        payload = event.to_dict()
        if data is not None:
            payload["data"] = data
        return f"event: {event.type}\ndata: {json.dumps(payload, ensure_ascii=False)}\n\n"

    async def build_streaming_response(
        payload: ChatRequest,
        token: str | None,
        owner_id: str,
    ) -> StreamingResponse:
        if not payload.message.strip():
            raise HTTPException(status_code=422, detail="message 不能为空")
        conversation_id = payload.conversation_id or str(uuid4())
        system = normalize_system(payload.system)
        context = build_context(payload, conversation_id, system, owner_id)
        handle = await runs.start(owner_id=owner_id)

        async def execute() -> dict[str, Any]:
            result = await agent.run(
                message=payload.message,
                conversation_id=conversation_id,
                user_token=token,
                system=system,
                context=context,
                owner_id=owner_id,
                event_emitter=handle.emitter,
                cancellation=handle.cancellation,
            )
            store.append_turn(conversation_id, payload.message, result, system=system, owner_id=owner_id)
            return response_data(conversation_id, system, result)

        task = asyncio.create_task(execute(), name=f"agent-run-{handle.run_id}")
        await runs.attach_task(handle.run_id, task)

        async def event_stream():
            try:
                while True:
                    try:
                        event = await asyncio.wait_for(handle.queue.get(), timeout=STREAM_HEARTBEAT_SECONDS)
                    except TimeoutError:
                        yield ": keep-alive\n\n"
                        continue
                    terminal = event.type in {"run.completed", "run.cancelled", "run.failed"}
                    if event.type == "run.completed":
                        completed_data = await task
                        yield sse(event, completed_data)
                    else:
                        yield sse(event)
                    if terminal:
                        if not task.done():
                            with suppress(asyncio.CancelledError, Exception):
                                await task
                        break
            finally:
                if not task.done():
                    await runs.cancel(handle.run_id)
                with suppress(asyncio.CancelledError, Exception):
                    await task
                await runs.finish(handle.run_id)

        return StreamingResponse(
            event_stream(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache, no-transform",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )

    @app.get("/health")
    def health() -> dict[str, str]:
        return {
            "status": "UP",
            "webMcp": "remote" if use_remote_web_mcp else "builtin",
        }

    @app.post("/api/agent/chat")
    async def chat(
        payload: ChatRequest,
        token: str | None = Header(default=None),
        accept: str | None = Header(default=None),
        login_user_id: str | None = Header(default=None, alias="X-Login-User-Id"),
    ) -> Any:
        owner_id = normalize_owner_id(login_user_id)
        if (payload.action or "").strip().lower() == "cancel":
            run_id = (payload.run_id or "").strip()
            cancelled = await runs.cancel(run_id, owner_id=owner_id) if run_id else False
            return success({"runId": run_id, "cancelled": cancelled})
        if "text/event-stream" in (accept or "").lower():
            return await build_streaming_response(payload, token, owner_id)
        if not payload.message.strip():
            raise HTTPException(status_code=422, detail="message 不能为空")
        conversation_id = payload.conversation_id or str(uuid4())
        system = normalize_system(payload.system)
        context = build_context(payload, conversation_id, system, owner_id)
        result = await agent.run(
            message=payload.message,
            conversation_id=conversation_id,
            user_token=token,
            system=system,
            context=context,
            owner_id=owner_id,
        )
        store.append_turn(conversation_id, payload.message, result, system=system, owner_id=owner_id)
        return success(response_data(conversation_id, system, result))

    @app.post("/api/agent/chat/stream")
    async def chat_stream(
        payload: ChatRequest,
        token: str | None = Header(default=None),
        login_user_id: str | None = Header(default=None, alias="X-Login-User-Id"),
    ) -> StreamingResponse:
        return await build_streaming_response(payload, token, normalize_owner_id(login_user_id))

    @app.post("/api/agent/runs/{run_id}/cancel")
    async def cancel_run(
        run_id: str,
        login_user_id: str | None = Header(default=None, alias="X-Login-User-Id"),
    ) -> dict[str, Any]:
        cancelled = await runs.cancel(run_id, owner_id=normalize_owner_id(login_user_id))
        return success({"runId": run_id, "cancelled": cancelled})

    @app.get("/api/agent/conversations")
    def conversations(
        system: str | None = Query(default=None),
        login_user_id: str | None = Header(default=None, alias="X-Login-User-Id"),
    ) -> dict[str, Any]:
        normalized_system = normalize_system(system) if system else None
        return success({
            "items": store.list_conversations(
                system=normalized_system,
                owner_id=normalize_owner_id(login_user_id),
            )
        })

    @app.get("/api/agent/conversations/{conversation_id}")
    def conversation_detail(
        conversation_id: str,
        system: str | None = Query(default=None),
        login_user_id: str | None = Header(default=None, alias="X-Login-User-Id"),
    ) -> dict[str, Any]:
        normalized_system = normalize_system(system) if system else None
        return success(store.get_conversation(
            conversation_id,
            system=normalized_system,
            owner_id=normalize_owner_id(login_user_id),
        ))

    @app.delete("/api/agent/conversations/{conversation_id}")
    def delete_conversation(
        conversation_id: str,
        system: str | None = Query(default=None),
        login_user_id: str | None = Header(default=None, alias="X-Login-User-Id"),
    ) -> dict[str, Any]:
        normalized_system = normalize_system(system) if system else None
        deleted = store.delete_conversation(
            conversation_id,
            system=normalized_system,
            owner_id=normalize_owner_id(login_user_id),
        )
        return success({"deleted": deleted})

    @app.get("/api/agent/reports/{report_id}")
    def report_detail(report_id: str) -> dict[str, Any]:
        return success(store.get_report(report_id))

    app.mount("/mcp", web_mcp_app, name="builtin-web-mcp")
    return app

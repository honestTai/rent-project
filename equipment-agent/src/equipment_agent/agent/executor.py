from __future__ import annotations

import asyncio
import time
from typing import Any

from equipment_agent.agent.tool_catalog import tool_display_name


class ToolExecutionMixin:
    """Execute planned read-only tools and emit auditable lifecycle events.

    The concrete workflow supplies the individual SQL, knowledge, web, log and
    result-summary adapters. Keeping concurrency and event semantics here makes
    the orchestration loop independently understandable.
    """

    async def _execute_tools(self, state: dict[str, Any]) -> dict[str, Any]:
        state.setdefault("tool_results", {})
        gateway = self.gateway_factory(self.gateway_base_url, state.get("user_token")) if self.gateway_base_url else None
        calls = state.get("tool_calls", [])
        has_sql_chain = any(call.get("kind") in {"sql_schema", "sql_plan", "sql_query"} for call in calls)
        if has_sql_chain:
            for call in calls:
                await self._execute_one_tool(state, call, gateway)
        else:
            await asyncio.gather(*(self._execute_one_tool(state, call, gateway) for call in calls))
        state.setdefault("all_tool_calls", []).extend(dict(call) for call in calls)
        return state

    async def _execute_one_tool(self, state: dict[str, Any], call: dict[str, Any], gateway: Any | None) -> None:
        self._raise_if_cancelled(state)
        started_at = time.perf_counter()
        call["displayName"] = tool_display_name(str(call.get("name") or ""))
        await self._emit(
            state,
            "tool.started",
            {
                "name": call.get("name"),
                "displayName": call["displayName"],
                "readonly": call.get("readonly", True),
            },
        )
        try:
            kind = call.get("kind")
            if kind == "sql_schema":
                self._execute_sql_schema_tool(state, call)
            elif kind == "sql_plan":
                await self._execute_sql_plan_tool(state, call)
            elif kind == "sql_query":
                self._execute_sql_query_tool(state, call)
            elif kind == "knowledge":
                self._execute_knowledge_tool(state, call)
            elif kind == "web_search":
                await self._execute_web_search_tool(state, call)
            elif kind == "current_time":
                await self._execute_current_time_tool(state, call)
            elif kind == "docker_logs":
                await asyncio.to_thread(self._execute_docker_logs_tool, state, call)
            else:
                await self._execute_gateway_tool(state, call, gateway)
            self._raise_if_cancelled(state)
        finally:
            call["durationMs"] = max(0, round((time.perf_counter() - started_at) * 1000))
            event_type = "tool.failed" if call.get("status") == "failed" else "tool.completed"
            await self._emit(
                state,
                event_type,
                {
                    "name": call.get("name"),
                    "displayName": call.get("displayName"),
                    "readonly": call.get("readonly", True),
                    "status": call.get("status", "failed"),
                    "durationMs": call["durationMs"],
                    "summary": call.get("resultSummary") or call.get("reason") or call.get("summary"),
                },
            )

    async def _execute_gateway_tool(self, state: dict[str, Any], call: dict[str, Any], gateway: Any | None) -> None:
        method = str(call.get("method") or "")
        path = str(call.get("path") or "")
        payload = call.get("payload") if isinstance(call.get("payload"), dict) else {}
        if not method or not path:
            call["status"] = "skipped"
            call["reason"] = "工具未配置请求参数"
            return
        if gateway is None:
            call["status"] = "skipped"
            call["reason"] = "只读工具已规划；当前未配置网关地址，未发起业务请求"
            return
        try:
            result = await gateway.request(method, path, payload)
        except Exception as exc:
            call["status"] = "failed"
            call["reason"] = str(exc)
            return
        call["status"] = "success"
        call["resultSummary"] = self._summarize_tool_result(call["name"], result)
        state["tool_results"][call["name"]] = result

    async def _emit(self, state: dict[str, Any], event_type: str, data: dict[str, Any]) -> None:
        emitter = state.get("event_emitter")
        if emitter is not None:
            await emitter.emit(event_type, data)

    def _raise_if_cancelled(self, state: dict[str, Any]) -> None:
        cancellation = state.get("cancellation")
        if cancellation is not None:
            cancellation.raise_if_cancelled()

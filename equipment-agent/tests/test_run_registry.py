from __future__ import annotations

import asyncio

from equipment_agent.agent.events import RunEventEmitter
from equipment_agent.agent.run_registry import RunRegistry


async def test_event_emitter_assigns_monotonic_sequence_numbers() -> None:
    queue: asyncio.Queue = asyncio.Queue()
    emitter = RunEventEmitter("run-1", queue)

    await emitter.emit("run.started", {"message": "hello"})
    await emitter.emit("plan.ready", {"tools": []})

    first = await queue.get()
    second = await queue.get()
    assert first.type == "run.started"
    assert first.run_id == "run-1"
    assert first.sequence == 1
    assert first.data == {"message": "hello"}
    assert second.sequence == 2
    assert second.timestamp >= first.timestamp


async def test_registry_cancels_active_run_and_removes_finished_run() -> None:
    registry = RunRegistry()
    handle = await registry.start("run-1")

    assert await registry.cancel("missing") is False
    assert await registry.cancel("run-1") is True
    assert handle.cancellation.cancelled is True
    assert await registry.is_active("run-1") is True

    await registry.finish("run-1")

    assert await registry.is_active("run-1") is False
    assert await registry.cancel("run-1") is False


async def test_registry_rejects_cancellation_from_another_owner() -> None:
    registry = RunRegistry()
    handle = await registry.start("owned-run", owner_id="101")

    assert await registry.cancel("owned-run", owner_id="202") is False
    assert handle.cancellation.cancelled is False
    assert await registry.cancel("owned-run", owner_id="101") is True
    assert handle.cancellation.cancelled is True

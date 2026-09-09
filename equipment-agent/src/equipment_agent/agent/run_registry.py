from __future__ import annotations

import asyncio
from dataclasses import dataclass, field
from uuid import uuid4

from equipment_agent.agent.events import RunEvent, RunEventEmitter


class CancellationToken:
    def __init__(self) -> None:
        self._event = asyncio.Event()

    @property
    def cancelled(self) -> bool:
        return self._event.is_set()

    def cancel(self) -> None:
        self._event.set()

    def raise_if_cancelled(self) -> None:
        if self.cancelled:
            raise asyncio.CancelledError()


@dataclass
class RunHandle:
    run_id: str
    owner_id: str = "legacy"
    cancellation: CancellationToken = field(default_factory=CancellationToken)
    queue: asyncio.Queue[RunEvent] = field(default_factory=asyncio.Queue)
    task: asyncio.Task | None = None

    @property
    def emitter(self) -> RunEventEmitter:
        emitter = getattr(self, "_emitter", None)
        if emitter is None:
            emitter = RunEventEmitter(self.run_id, self.queue)
            setattr(self, "_emitter", emitter)
        return emitter


class RunRegistry:
    def __init__(self) -> None:
        self._runs: dict[str, RunHandle] = {}
        self._lock = asyncio.Lock()

    async def start(self, run_id: str | None = None, owner_id: str = "legacy") -> RunHandle:
        handle = RunHandle(run_id=run_id or str(uuid4()), owner_id=owner_id)
        async with self._lock:
            if handle.run_id in self._runs:
                raise ValueError(f"run already active: {handle.run_id}")
            self._runs[handle.run_id] = handle
        return handle

    async def attach_task(self, run_id: str, task: asyncio.Task) -> bool:
        async with self._lock:
            handle = self._runs.get(run_id)
            if handle is None:
                return False
            handle.task = task
            return True

    async def get(self, run_id: str) -> RunHandle | None:
        async with self._lock:
            return self._runs.get(run_id)

    async def cancel(self, run_id: str, owner_id: str | None = None) -> bool:
        async with self._lock:
            handle = self._runs.get(run_id)
            if handle is None:
                return False
            if owner_id is not None and handle.owner_id != owner_id:
                return False
            handle.cancellation.cancel()
            task = handle.task
        if task is not None and not task.done():
            task.cancel()
        return True

    async def finish(self, run_id: str) -> None:
        async with self._lock:
            self._runs.pop(run_id, None)

    async def is_active(self, run_id: str) -> bool:
        async with self._lock:
            return run_id in self._runs

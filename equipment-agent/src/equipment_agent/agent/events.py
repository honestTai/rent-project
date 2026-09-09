from __future__ import annotations

import asyncio
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any


@dataclass(frozen=True)
class RunEvent:
    type: str
    run_id: str
    sequence: int
    timestamp: str
    data: dict[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return {
            "type": self.type,
            "runId": self.run_id,
            "sequence": self.sequence,
            "timestamp": self.timestamp,
            "data": self.data,
        }


class RunEventEmitter:
    def __init__(self, run_id: str, queue: asyncio.Queue[RunEvent]) -> None:
        self.run_id = run_id
        self.queue = queue
        self._sequence = 0
        self._lock = asyncio.Lock()

    async def emit(self, event_type: str, data: dict[str, Any] | None = None) -> RunEvent:
        async with self._lock:
            self._sequence += 1
            event = RunEvent(
                type=event_type,
                run_id=self.run_id,
                sequence=self._sequence,
                timestamp=datetime.now(timezone.utc).isoformat(),
                data=dict(data or {}),
            )
        await self.queue.put(event)
        return event

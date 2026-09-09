from __future__ import annotations

import hashlib
import math
import os
import uuid
from dataclasses import dataclass
from typing import Any

from qdrant_client import QdrantClient
from qdrant_client.http import models


VECTOR_SIZE = 128


@dataclass(frozen=True)
class VectorMemory:
    text: str
    metadata: dict[str, Any]
    score: float


class VectorMemoryStore:
    """Qdrant-backed memory isolated from business databases."""

    def __init__(
        self,
        client: QdrantClient | None = None,
        collection_name: str = "equipment_agent_memories",
        vector_size: int = VECTOR_SIZE,
    ) -> None:
        self.client = client or self._build_client()
        self.collection_name = collection_name
        self.vector_size = vector_size
        self._ensure_collection(self.collection_name)

    def _build_client(self) -> QdrantClient:
        url = os.getenv("AGENT_QDRANT_URL", "").strip()
        api_key = os.getenv("AGENT_QDRANT_API_KEY", "").strip() or None
        path = os.getenv("AGENT_QDRANT_PATH", "").strip()
        if url:
            return QdrantClient(url=url, api_key=api_key)
        if path:
            return QdrantClient(path=path)
        return QdrantClient(":memory:")

    def remember(self, text: str, metadata: dict[str, Any] | None = None) -> str:
        point_id = str(uuid.uuid4())
        safe_metadata = metadata or {}
        collection_name = self._collection_name(safe_metadata.get("system"))
        self._ensure_collection(collection_name)
        self.client.upsert(
            collection_name=collection_name,
            points=[
                models.PointStruct(
                    id=point_id,
                    vector=self._embed(text),
                    payload={"text": text, "metadata": safe_metadata},
                )
            ],
        )
        return point_id

    def search(
        self,
        query: str,
        limit: int = 5,
        system: str | None = None,
        owner_id: str | None = None,
    ) -> list[VectorMemory]:
        if limit <= 0:
            return []
        collection_name = self._collection_name(system)
        if not self._collection_exists(collection_name):
            return []
        result = self.client.query_points(
            collection_name=collection_name,
            query=self._embed(query),
            query_filter=self._owner_filter(owner_id),
            limit=limit,
            with_payload=True,
        )
        points = getattr(result, "points", result)
        rows: list[VectorMemory] = []
        for point in points:
            payload = point.payload or {}
            rows.append(
                VectorMemory(
                    text=str(payload.get("text") or ""),
                    metadata=dict(payload.get("metadata") or {}),
                    score=float(point.score or 0),
                )
            )
        return rows

    def _owner_filter(self, owner_id: str | None) -> models.Filter | None:
        if owner_id is None:
            return None
        return models.Filter(should=[
            models.FieldCondition(
                key="metadata.ownerId",
                match=models.MatchValue(value=owner_id),
            ),
            models.Filter(must=[
                models.IsEmptyCondition(is_empty=models.PayloadField(key="metadata.ownerId")),
                models.IsEmptyCondition(is_empty=models.PayloadField(key="metadata.conversationId")),
            ]),
        ])

    def _collection_name(self, system: Any | None) -> str:
        if not system:
            return self.collection_name
        safe_system = "".join(char for char in str(system).lower() if char.isalnum() or char in {"_", "-"})
        return f"{self.collection_name}_{safe_system or 'default'}"

    def _collection_exists(self, collection_name: str) -> bool:
        existing = {collection.name for collection in self.client.get_collections().collections}
        return collection_name in existing

    def _ensure_collection(self, collection_name: str) -> None:
        if self._collection_exists(collection_name):
            return
        self.client.create_collection(
            collection_name=collection_name,
            vectors_config=models.VectorParams(size=self.vector_size, distance=models.Distance.COSINE),
        )

    def _embed(self, text: str) -> list[float]:
        vector = [0.0] * self.vector_size
        tokens = _tokens(text)
        for token in tokens:
            digest = hashlib.sha256(token.encode("utf-8")).digest()
            index = int.from_bytes(digest[:4], "big") % self.vector_size
            sign = 1.0 if digest[4] % 2 == 0 else -1.0
            vector[index] += sign
        norm = math.sqrt(sum(value * value for value in vector))
        if norm == 0:
            return vector
        return [value / norm for value in vector]


def _tokens(text: str) -> list[str]:
    cleaned = text.strip().lower()
    tokens: list[str] = []
    current = []
    for char in cleaned:
        if char.isascii() and char.isalnum():
            current.append(char)
            continue
        if current:
            tokens.append("".join(current))
            current = []
        if not char.isspace():
            tokens.append(char)
    if current:
        tokens.append("".join(current))
    if len(tokens) > 1:
        tokens.extend([tokens[index] + tokens[index + 1] for index in range(len(tokens) - 1)])
    return tokens or [cleaned]

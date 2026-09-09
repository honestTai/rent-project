from __future__ import annotations

from typing import Any


SENSITIVE_KEYS = {
    "apikey",
    "api_key",
    "token",
    "secret",
    "password",
    "privatekey",
    "private_key",
    "sign",
    "signature",
    "cookie",
    "idcard",
    "id_card",
    "mobile",
    "phone",
}


def build_chart_report(
    title: str,
    rows: list[dict[str, Any]],
    x_key: str,
    value_keys: list[str],
    chart_type: str,
    source_tool: str,
    value_labels: dict[str, str] | None = None,
    summary: str = "",
    unit_labels: dict[str, str] | None = None,
) -> dict[str, Any]:
    labels = value_labels or {}
    dimensions = [x_key] + [key for key in value_keys if key != x_key]
    display_columns = [{"key": key, "label": labels.get(key, key)} for key in dimensions]
    masked_rows = [_mask_row(row) for row in rows]
    report = {
        "type": chart_type,
        "title": title,
        "dimensions": dimensions,
        "series": [{"key": key, "name": labels.get(key, key)} for key in value_keys],
        "rows": masked_rows,
        "displayColumns": display_columns,
        "displayRows": [
            {column["label"]: row.get(column["key"]) for column in display_columns}
            for row in masked_rows
        ],
        "sourceTool": source_tool,
    }
    if summary:
        report["summary"] = summary
    if unit_labels:
        report["unitLabels"] = unit_labels
    return report


def _mask_row(row: dict[str, Any]) -> dict[str, Any]:
    return {key: ("***" if _is_sensitive_key(key) else value) for key, value in row.items()}


def _is_sensitive_key(key: str) -> bool:
    normalized = key.replace("-", "_").lower()
    compact = normalized.replace("_", "")
    return normalized in SENSITIVE_KEYS or compact in SENSITIVE_KEYS

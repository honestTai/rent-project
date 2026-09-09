from __future__ import annotations

from dataclasses import dataclass


MUTATION_WORDS = (
    "sync",
    "generate",
    "save",
    "update",
    "delete",
    "confirm",
    "deduct",
    "refund",
    "send",
    "close",
    "create",
    "import",
    "up",
    "down",
)


@dataclass(frozen=True)
class ToolRequest:
    method: str
    path: str


@dataclass(frozen=True)
class PolicyDecision:
    allowed: bool
    reason: str


class ReadOnlyPolicy:
    ALLOWLIST = {
        ("GET", "/api/platform/monitor/overview"),
        ("GET", "/api/platform/system-log/sources"),
        ("POST", "/api/platform/system-log/query"),
        ("POST", "/api/web/analytics/dashboard"),
        ("POST", "/api/web/analytics/subjects"),
        ("POST", "/api/web/order-oper-logs/page"),
        ("POST", "/api/web/order-oper-logs/by-order-id"),
        ("POST", "/api/web/order-oper-logs/by-order-no"),
        ("POST", "/api/web/goods/sync-logs/page"),
        ("POST", "/api/web/rent-component/page"),
        ("POST", "/api/web/rent-component/detail"),
        ("POST", "/api/web/rent-component/return-record"),
        ("POST", "/api/web/rent-component/risk-detail"),
        ("POST", "/api/web/rent-component/deposit/query"),
        ("POST", "/api/web/rent-component/deposit/deduct-records"),
        ("POST", "/api/home/dashboard"),
        ("POST", "/api/home/report-center/page"),
        ("GET", "/api/home/report-center/{id}"),
        ("POST", "/api/log/operationLogList"),
        ("POST", "/api/second/analysis/dashboard"),
        ("POST", "/api/second/analysis/report-center/page"),
        ("POST", "/api/second/logPage"),
        ("POST", "/api/second/douyin/logs/messages"),
        ("POST", "/api/second/douyin/logs/apis"),
    }

    def check(self, request: ToolRequest) -> PolicyDecision:
        method = request.method.upper()
        path = self._normalize_path(request.path)
        lower_path = path.lower()
        if (method, path) in self.ALLOWLIST or any(
            allow_method == method and self._matches_template(path, allow_path)
            for allow_method, allow_path in self.ALLOWLIST
        ):
            return PolicyDecision(True, "allowed read-only endpoint")
        if any(f"/{word}" in lower_path or lower_path.endswith(word) for word in MUTATION_WORDS):
            return PolicyDecision(False, "只读策略阻止调用疑似业务变更接口")
        return PolicyDecision(False, "endpoint not in read-only allowlist")

    def _normalize_path(self, path: str) -> str:
        return "/" + path.strip().split("?", 1)[0].strip("/")

    def _matches_template(self, path: str, template: str) -> bool:
        path_parts = path.strip("/").split("/")
        template_parts = template.strip("/").split("/")
        if len(path_parts) != len(template_parts):
            return False
        for path_part, template_part in zip(path_parts, template_parts):
            if template_part.startswith("{") and template_part.endswith("}"):
                if not path_part:
                    return False
                continue
            if path_part != template_part:
                return False
        return True

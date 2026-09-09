from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass, field
from typing import Callable, Sequence


DEFAULT_CONTAINER_PATTERNS = {
    "alipay": ["alipay", "equipment-alipay"],
    "rental": ["rental", "equipment-rental"],
    "secondhand": ["secondhand", "equipment-secondhand"],
    "platform": ["platform", "equipment-platform"],
    "gateway": ["gateway", "equipment-gateway"],
}

SENSITIVE_LOG_PATTERNS = [
    re.compile(r"(?i)(token|authorization|cookie|secret|password|passwd|sign|signature)=([^&\s]+)"),
    re.compile(r"1[3-9]\d{9}"),
    re.compile(r"(?i)(idcard|identity|身份证)[=:：]?\s*([0-9xX*]{8,})"),
]


@dataclass
class CommandResult:
    returncode: int
    stdout: str = ""
    stderr: str = ""


Runner = Callable[[Sequence[str], int], CommandResult]


def default_runner(args: Sequence[str], timeout: int) -> CommandResult:
    completed = subprocess.run(
        list(args),
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
    )
    return CommandResult(completed.returncode, completed.stdout or "", completed.stderr or "")


@dataclass
class DockerLogsClient:
    container_patterns: dict[str, list[str]] = field(default_factory=lambda: dict(DEFAULT_CONTAINER_PATTERNS))
    docker_binary: str = "docker"
    timeout_seconds: int = 6
    default_tail: int = 200
    default_since: str = "30m"
    runner: Runner = default_runner

    def query(self, system: str, keyword: str = "", *, tail: int | None = None, since: str | None = None) -> dict:
        containers = self._containers_for_system(system)
        if not containers:
            return {
                "available": False,
                "summary": "没有匹配到该业务系统的容器",
                "containers": [],
            }

        limit = max(20, min(int(tail or self.default_tail), 1000))
        since_value = self._safe_since(since or self.default_since)
        keyword_text = self._safe_keyword(keyword)
        results = []
        total_matches = 0
        total_lines = 0

        for container in containers[:4]:
            command = [self.docker_binary, "logs", "--tail", str(limit), "--since", since_value, container]
            try:
                completed = self.runner(command, self.timeout_seconds)
            except Exception as exc:
                results.append({
                    "container": container,
                    "status": "failed",
                    "summary": f"读取容器日志失败：{exc}",
                    "samples": [],
                })
                continue

            if completed.returncode != 0:
                results.append({
                    "container": container,
                    "status": "failed",
                    "summary": self._sanitize_line(completed.stderr.strip() or "容器日志读取失败"),
                    "samples": [],
                })
                continue

            lines = [self._sanitize_line(line) for line in completed.stdout.splitlines() if line.strip()]
            matched = self._filter_lines(lines, keyword_text)
            samples = matched[:8] if matched else lines[-8:]
            total_matches += len(matched)
            total_lines += len(lines)
            results.append({
                "container": container,
                "status": "success",
                "summary": self._container_summary(container, lines, matched, keyword_text),
                "samples": samples,
                "lineCount": len(lines),
                "matchCount": len(matched),
            })

        success_count = sum(1 for item in results if item.get("status") == "success")
        return {
            "available": success_count > 0,
            "summary": f"已读取 {success_count} 个容器的最近日志，扫描 {total_lines} 行，命中 {total_matches} 行。",
            "containers": results,
            "lineCount": total_lines,
            "matchCount": total_matches,
        }

    def _containers_for_system(self, system: str) -> list[str]:
        patterns = self.container_patterns.get(system) or self.container_patterns.get("alipay") or []
        names = self._docker_container_names()
        if not names:
            return patterns[:4]
        selected = []
        for name in names:
            normalized = name.lower()
            if any(pattern.lower() in normalized for pattern in patterns):
                selected.append(name)
        return selected[:4]

    def _docker_container_names(self) -> list[str]:
        try:
            completed = self.runner([self.docker_binary, "ps", "--format", "{{.Names}}"], self.timeout_seconds)
        except Exception:
            return []
        if completed.returncode != 0:
            return []
        return [line.strip() for line in completed.stdout.splitlines() if line.strip()]

    def _filter_lines(self, lines: list[str], keyword: str) -> list[str]:
        if keyword:
            terms = self._keyword_terms(keyword)
            if terms:
                return [line for line in lines if any(term.lower() in line.lower() for term in terms)]
        return [
            line for line in lines
            if re.search(r"(?i)\b(error|exception|warn|failed|fail|timeout)\b|失败|异常|错误|超时", line)
        ]

    def _keyword_terms(self, keyword: str) -> list[str]:
        text = keyword or ""
        terms = [term for term in re.split(r"\s+", text) if len(term) >= 2]
        if any(word in text for word in ["支付", "扣款", "押金", "预授权"]):
            terms.extend(["payment", "pay", "deposit", "deduct"])
        if any(word in text for word in ["失败", "报错", "错误", "异常"]):
            terms.extend(["failed", "fail", "error", "exception"])
        if any(word in text for word in ["超时", "慢", "卡住"]):
            terms.extend(["timeout", "timed out", "slow"])
        if any(word in text for word in ["回调", "通知", "notify"]):
            terms.extend(["callback", "notify", "notification"])
        if any(word in text for word in ["同步", "抖音", "抖店"]):
            terms.extend(["sync", "douyin"])
        return list(dict.fromkeys(term for term in terms if term.strip()))

    def _container_summary(self, container: str, lines: list[str], matched: list[str], keyword: str) -> str:
        if not lines:
            return f"{container} 最近没有日志输出"
        if matched:
            label = "关键字" if keyword else "异常关键字"
            return f"{container} 最近 {len(lines)} 行日志中命中 {len(matched)} 行{label}"
        return f"{container} 最近 {len(lines)} 行日志没有命中明显异常"

    def _sanitize_line(self, line: str) -> str:
        text = str(line or "")
        for pattern in SENSITIVE_LOG_PATTERNS:
            text = pattern.sub(lambda match: f"{match.group(1)}=***" if match.lastindex and match.lastindex >= 1 else "***", text)
        return text[:500]

    def _safe_keyword(self, keyword: str) -> str:
        return re.sub(r"[^\w\u4e00-\u9fff.\-: ]+", " ", str(keyword or ""))[:120].strip()

    def _safe_since(self, since: str) -> str:
        text = str(since or self.default_since).strip()
        return text if re.fullmatch(r"\d{1,4}(s|m|h)", text) else self.default_since

from __future__ import annotations

from equipment_agent.tools.docker_logs import CommandResult, DockerLogsClient


def test_docker_logs_client_reads_allowlisted_container_and_masks_sensitive_values():
    calls = []

    def runner(args, timeout):
        calls.append(list(args))
        if args[:3] == ["docker", "ps", "--format"]:
            return CommandResult(0, "equipment-alipay-service\nmysql\n", "")
        if args[:2] == ["docker", "logs"]:
            return CommandResult(
                0,
                "INFO ok\nERROR payment failed token=abc123 phone=13800138000\n",
                "",
            )
        return CommandResult(1, "", "unexpected")

    client = DockerLogsClient(runner=runner)
    result = client.query("alipay", keyword="payment", tail=300, since="20m")

    assert calls[1] == ["docker", "logs", "--tail", "300", "--since", "20m", "equipment-alipay-service"]
    assert result["available"] is True
    sample = result["containers"][0]["samples"][0]
    assert "payment failed" in sample
    assert "abc123" not in sample
    assert "13800138000" not in sample


def test_docker_logs_client_rejects_unsafe_since_and_caps_tail():
    calls = []

    def runner(args, timeout):
        calls.append(list(args))
        if args[:3] == ["docker", "ps", "--format"]:
            return CommandResult(0, "", "")
        return CommandResult(0, "", "")

    client = DockerLogsClient(runner=runner, container_patterns={"alipay": ["equipment-alipay"]})
    client.query("alipay", keyword="x; rm -rf /", tail=9999, since=";bad")

    assert calls[-1] == ["docker", "logs", "--tail", "1000", "--since", "30m", "equipment-alipay"]


def test_docker_logs_client_matches_business_alias_and_keyword_family():
    calls = []

    def runner(args, timeout):
        calls.append(list(args))
        if args[:3] == ["docker", "ps", "--format"]:
            return CommandResult(0, "ems-gateway\nems-alipay-pay\nems-secondhand-sync\n", "")
        if args[:2] == ["docker", "logs"]:
            return CommandResult(
                0,
                "INFO normal\nWARN gateway timeout on /notify\nERROR alipay payment failed\n",
                "",
            )
        return CommandResult(1, "", "unexpected")

    client = DockerLogsClient(
        runner=runner,
        container_patterns={
            "alipay": ["alipay", "pay", "notify"],
            "gateway": ["gateway"],
            "secondhand": ["secondhand", "sync"],
        },
    )

    result = client.query("alipay", keyword="扣款失败")

    assert calls[1][-1] == "ems-alipay-pay"
    assert result["containers"][0]["matchCount"] >= 1
    assert any("payment failed" in sample for sample in result["containers"][0]["samples"])

import pytest

from equipment_agent.tools.policy import ReadOnlyPolicy, ToolRequest


def test_allows_known_readonly_endpoint():
    policy = ReadOnlyPolicy()

    request = ToolRequest(method="POST", path="/api/web/order-oper-logs/by-order-no")

    assert policy.check(request).allowed is True


def test_allows_exact_readonly_log_endpoint_with_mutation_keyword():
    policy = ReadOnlyPolicy()

    request = ToolRequest(method="POST", path="/api/web/goods/sync-logs/page")

    assert policy.check(request).allowed is True


def test_allows_readonly_template_endpoint():
    policy = ReadOnlyPolicy()

    request = ToolRequest(method="GET", path="/api/home/report-center/123")

    assert policy.check(request).allowed is True


@pytest.mark.parametrize(
    "path",
    [
        "/api/web/rent-component/sync",
        "/api/web/report-center/generate",
        "/api/web/goods/update",
        "/api/web/rent-component/deposit/deduct",
        "/api/platform/internal/config/save",
    ],
)
def test_blocks_mutating_endpoints_even_when_method_is_post(path):
    policy = ReadOnlyPolicy()

    decision = policy.check(ToolRequest(method="POST", path=path))

    assert decision.allowed is False
    assert "只读" in decision.reason


def test_blocks_unknown_endpoint_by_default():
    policy = ReadOnlyPolicy()

    decision = policy.check(ToolRequest(method="GET", path="/api/web/unknown/read"))

    assert decision.allowed is False
    assert "allowlist" in decision.reason

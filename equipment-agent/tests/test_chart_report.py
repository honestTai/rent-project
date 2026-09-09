from equipment_agent.reports.chart_report import build_chart_report
from equipment_agent.agent.workflow import AgentWorkflow, _human_value, _localize_visible_text, _metric_label, _unit_labels


def test_dynamic_sql_aliases_get_business_labels_and_units():
    assert _metric_label("month") == "月份"
    assert _metric_label("order_count") == "订单数"
    assert _metric_label("total_paid_amount_yuan") == "已支付金额"
    assert _metric_label("paid_amount_total") == "已支付金额"
    assert _metric_label("total_runt") == "租金金额"
    assert _metric_label("total_rent") == "租金金额"
    assert _metric_label("totalRent") == "租金金额"
    assert _metric_label("profit_amount") == "利润"
    assert _unit_labels(["order_count", "total_paid_amount_yuan", "paid_amount_total", "total_runt", "total_rent", "totalRent", "profit_amount"]) == {
        "total_paid_amount_yuan": "元",
        "paid_amount_total": "元",
        "total_runt": "元",
        "total_rent": "元",
        "totalRent": "元",
        "profit_amount": "元",
    }
    assert _human_value(10501, "totalRent") == "10501.00 元"
    assert _localize_visible_text("租金收入按runt求和，totalRent为10501") == "租金收入按租金金额求和，租金金额为10501"


def test_builds_chart_spec_from_rows():
    result = build_chart_report(
        title="近7天订单趋势",
        rows=[
            {"date": "2026-07-01", "orders": 3, "amountYuan": 1200.5},
            {"date": "2026-07-02", "orders": 5, "amountYuan": 1800},
        ],
        x_key="date",
        value_keys=["orders", "amountYuan"],
        chart_type="line",
        source_tool="fetch_alipay_analytics_dashboard",
    )

    assert result["type"] == "line"
    assert result["title"] == "近7天订单趋势"
    assert result["dimensions"] == ["date", "orders", "amountYuan"]
    assert result["series"] == [
        {"key": "orders", "name": "orders"},
        {"key": "amountYuan", "name": "amountYuan"},
    ]
    assert result["rows"][0]["orders"] == 3
    assert result["sourceTool"] == "fetch_alipay_analytics_dashboard"


def test_chart_report_masks_sensitive_fields():
    result = build_chart_report(
        title="日志命中",
        rows=[{"orderNo": "A001", "apiKey": "abc", "token": "def"}],
        x_key="orderNo",
        value_keys=["apiKey"],
        chart_type="bar",
        source_tool="query_system_logs",
    )

    assert result["rows"][0]["apiKey"] == "***"
    assert result["rows"][0]["token"] == "***"


def test_chart_report_can_attach_display_summary():
    result = build_chart_report(
        title="6月销售趋势",
        rows=[{"date": "2026-06-01", "orderCount": 2, "paidAmountYuan": 100.5}],
        x_key="date",
        value_keys=["orderCount", "paidAmountYuan"],
        chart_type="line",
        source_tool="execute_alipay_readonly_sql",
        value_labels={"orderCount": "订单数", "paidAmountYuan": "已支付金额"},
        summary="共 2 笔，100.50 元",
        unit_labels={"paidAmountYuan": "元"},
    )

    assert result["summary"] == "共 2 笔，100.50 元"
    assert result["unitLabels"]["paidAmountYuan"] == "元"


def test_chart_report_exposes_chinese_display_rows_without_losing_machine_keys():
    result = build_chart_report(
        title="今日收益",
        rows=[{"date": "2026-07-09", "revenue": 16300, "profit": 1345}],
        x_key="date",
        value_keys=["revenue", "profit"],
        chart_type="bar",
        source_tool="execute_secondhand_readonly_sql",
        value_labels={"date": "日期", "revenue": "销售额", "profit": "利润"},
        unit_labels={"revenue": "元", "profit": "元"},
    )

    assert result["rows"][0]["revenue"] == 16300
    assert result["displayColumns"] == [
        {"key": "date", "label": "日期"},
        {"key": "revenue", "label": "销售额"},
        {"key": "profit", "label": "利润"},
    ]
    assert result["displayRows"][0] == {"日期": "2026-07-09", "销售额": 16300, "利润": 1345}


def test_secondhand_trend_uses_real_dto_fields_and_keeps_zero():
    workflow = AgentWorkflow.__new__(AgentWorkflow)

    rows = workflow._extract_trend_rows(
        {
            "data": {
                "trend": [
                    {
                        "dateLabel": "2026-07-10",
                        "orderCount": 19,
                        "salesAmount": 0,
                        "profitAmount": 0,
                    }
                ]
            }
        },
        system="secondhand",
    )

    assert rows == [
        {
            "date": "2026-07-10",
            "orders": 19,
            "salesAmountYuan": 0,
            "profitAmountYuan": 0,
        }
    ]


def test_rental_trend_supports_dashboard_datetime_fields():
    workflow = AgentWorkflow.__new__(AgentWorkflow)

    rows = workflow._extract_trend_rows(
        {
            "data": {
                "trend": [
                    {"dateTime": "2026-07-10", "nowOrder": 3, "nowMoney": 560.5}
                ]
            }
        },
        system="rental",
    )

    assert rows == [{"date": "2026-07-10", "orders": 3, "amountYuan": 560.5}]


def test_alipay_trend_supports_dashboard_fields_and_keeps_zero():
    workflow = AgentWorkflow.__new__(AgentWorkflow)

    rows = workflow._extract_trend_rows(
        {
            "data": {
                "trend": [
                    {"date": "2026-07-10", "orders": 0, "totalAmount": 0}
                ]
            }
        },
        system="alipay",
    )

    assert rows == [{"date": "2026-07-10", "orders": 0, "amountYuan": 0}]


def test_secondhand_today_report_has_real_title_and_chinese_columns():
    workflow = AgentWorkflow.__new__(AgentWorkflow)
    state = {
        "intent": "analysis_report",
        "system": "secondhand",
        "message": "分析今天二手交易趋势",
        "conversation_context": {"timeRange": "今天"},
        "tool_results": {
            "fetch_secondhand_analysis_dashboard": {
                "data": {
                    "trend": [
                        {
                            "dateLabel": "2026-07-10",
                            "orderCount": 2,
                            "salesAmount": 1000,
                            "profitAmount": 120,
                        }
                    ]
                }
            }
        },
    }

    workflow._build_report(state)

    chart = state["charts"][0]
    assert chart["title"] == "二手交易今日分析趋势"
    assert chart["displayColumns"] == [
        {"key": "date", "label": "日期"},
        {"key": "orders", "label": "订单数"},
        {"key": "salesAmountYuan", "label": "销售额"},
        {"key": "profitAmountYuan", "label": "利润"},
    ]


def test_analysis_report_does_not_emit_chart_for_blank_dates():
    workflow = AgentWorkflow.__new__(AgentWorkflow)
    state = {
        "intent": "analysis_report",
        "system": "secondhand",
        "message": "分析二手交易趋势",
        "tool_results": {
            "fetch_secondhand_analysis_dashboard": {
                "data": {"trend": [{"orderCount": 2, "salesAmount": 1000, "profitAmount": 120}]}
            }
        },
    }

    workflow._build_report(state)

    assert state["charts"] == []


def test_secondhand_today_analysis_payload_uses_exact_date_range():
    workflow = AgentWorkflow.__new__(AgentWorkflow)
    workflow._today_text = lambda: "2026-07-10"
    state = {
        "system": "secondhand",
        "message": "分析今天二手交易趋势",
        "context": {},
    }

    payload = workflow._analysis_payload(state)

    assert payload["startTime"] == "2026-07-10"
    assert payload["endTime"] == "2026-07-10"
    assert "periodType" not in payload

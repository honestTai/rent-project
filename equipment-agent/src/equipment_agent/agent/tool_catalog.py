from __future__ import annotations

from dataclasses import dataclass
from typing import Any


TOOL_DISPLAY_NAMES = {
    "current_time": "通过联网 MCP 查询当前时间",
    "search_web": "通过联网 MCP 检索公开资料",
    "query_monitor_overview": "查询服务监控概览",
    "list_system_log_sources": "读取日志来源",
    "query_system_logs": "查询系统日志",
    "fetch_alipay_analytics_dashboard": "查询支付宝经营分析",
    "fetch_alipay_analytics_subjects": "查询支付宝分析专题",
    "query_alipay_order_oper_logs": "查询支付宝订单操作记录",
    "query_alipay_order_oper_logs_by_order_id": "按订单标识查询操作记录",
    "query_alipay_order_oper_logs_by_order_no": "按订单号查询操作记录",
    "query_alipay_goods_sync_logs": "查询支付宝商品同步日志",
    "query_alipay_rent_component_page": "查询支付宝租赁订单",
    "query_alipay_rent_component_detail": "查询支付宝订单详情",
    "query_alipay_return_record": "查询归还记录",
    "query_alipay_risk_detail": "查询支付宝风控详情",
    "query_alipay_deposit": "查询押金与预授权",
    "query_alipay_deposit_deduct_records": "查询押金扣减记录",
    "fetch_rental_dashboard": "查询设备租赁经营概览",
    "query_rental_report_page": "查询设备租赁报表",
    "fetch_rental_report_detail": "查询设备租赁报表详情",
    "query_rental_operation_logs": "查询设备租赁操作日志",
    "fetch_secondhand_analysis_dashboard": "查询二手交易经营概览",
    "query_secondhand_report_page": "查询二手交易报表",
    "query_secondhand_operation_logs": "查询二手交易操作日志",
    "query_secondhand_douyin_message_logs": "查询抖音消息日志",
    "query_secondhand_douyin_api_logs": "查询抖音接口日志",
    "search_alipay_skill_and_official_docs": "检索支付宝项目知识",
    "search_rental_skill": "检索设备租赁项目知识",
    "search_secondhand_skill": "检索二手交易项目知识",
    "search_knowledge_base": "检索项目知识库",
}


@dataclass(frozen=True)
class ToolCapability:
    name: str
    display_name: str
    description: str
    systems: tuple[str, ...]
    category: str = "diagnosis"


DIRECT_TOOL_CAPABILITIES = (
    ToolCapability("query_monitor_overview", "查询服务监控概览", "查询微服务健康状态和监控概览。", ("alipay", "rental", "secondhand")),
    ToolCapability("list_system_log_sources", "读取日志来源", "读取平台日志中心当前可查询的日志来源。", ("alipay", "rental", "secondhand")),
    ToolCapability("query_system_logs", "查询系统日志", "按系统、时间和关键词查询平台日志中心。", ("alipay", "rental", "secondhand")),
    ToolCapability("fetch_alipay_analytics_dashboard", "查询支付宝经营分析", "查询支付宝租赁订单、金额和趋势数据。", ("alipay",), "analysis"),
    ToolCapability("fetch_alipay_analytics_subjects", "查询支付宝分析专题", "查询支付宝租赁经营专题和维度数据。", ("alipay",), "analysis"),
    ToolCapability("query_alipay_order_oper_logs", "查询支付宝订单操作记录", "查询支付宝订单操作台账和失败原因。", ("alipay",)),
    ToolCapability("query_alipay_order_oper_logs_by_order_no", "按订单号查询操作记录", "按订单号查询支付宝订单操作全过程。", ("alipay",)),
    ToolCapability("query_alipay_goods_sync_logs", "查询支付宝商品同步日志", "查询支付宝商品发布和同步失败日志。", ("alipay",)),
    ToolCapability("query_alipay_rent_component_page", "查询支付宝租赁订单", "查询支付宝租赁订单列表和状态。", ("alipay",)),
    ToolCapability("query_alipay_rent_component_detail", "查询支付宝订单详情", "查询单个支付宝租赁订单详情。", ("alipay",)),
    ToolCapability("query_alipay_return_record", "查询归还记录", "查询支付宝租赁订单的寄回和归还记录。", ("alipay",)),
    ToolCapability("query_alipay_risk_detail", "查询支付宝风控详情", "查询订单风控、租盾和高风险证据。", ("alipay",)),
    ToolCapability("query_alipay_deposit", "查询押金与预授权", "查询支付宝订单押金和预授权状态。", ("alipay",)),
    ToolCapability("query_alipay_deposit_deduct_records", "查询押金扣减记录", "查询押金扣减申请和结果记录。", ("alipay",)),
    ToolCapability("fetch_rental_dashboard", "查询设备租赁经营概览", "查询设备租赁订单、收益和趋势数据。", ("rental",), "analysis"),
    ToolCapability("query_rental_report_page", "查询设备租赁报表", "查询设备租赁报表中心列表。", ("rental",), "analysis"),
    ToolCapability("query_rental_operation_logs", "查询设备租赁操作日志", "查询设备租赁后台操作和异常日志。", ("rental",)),
    ToolCapability("fetch_secondhand_analysis_dashboard", "查询二手交易经营概览", "查询二手交易订单、销售额和利润趋势。", ("secondhand",), "analysis"),
    ToolCapability("query_secondhand_report_page", "查询二手交易报表", "查询二手交易报表中心列表。", ("secondhand",), "analysis"),
    ToolCapability("query_secondhand_operation_logs", "查询二手交易操作日志", "查询二手设备和订单操作日志。", ("secondhand",)),
    ToolCapability("query_secondhand_douyin_message_logs", "查询抖音消息日志", "查询抖音开放平台消息接收和处理日志。", ("secondhand",)),
    ToolCapability("query_secondhand_douyin_api_logs", "查询抖音接口日志", "查询抖音开放平台接口请求和失败日志。", ("secondhand",)),
    ToolCapability("search_alipay_skill_and_official_docs", "检索支付宝项目知识", "检索项目知识库和支付宝官方文档。", ("alipay",), "knowledge"),
    ToolCapability("search_rental_skill", "检索设备租赁项目知识", "检索设备租赁项目知识库。", ("rental",), "knowledge"),
    ToolCapability("search_secondhand_skill", "检索二手交易项目知识", "检索二手交易和抖音项目知识库。", ("secondhand",), "knowledge"),
)

CAPABILITY_BY_NAME = {capability.name: capability for capability in DIRECT_TOOL_CAPABILITIES}


def direct_tool_capability(name: str) -> ToolCapability | None:
    return CAPABILITY_BY_NAME.get(name)


def direct_tool_specs(system: str | None = None) -> list[dict[str, Any]]:
    specs = []
    for capability in DIRECT_TOOL_CAPABILITIES:
        if system and system not in capability.systems:
            continue
        specs.append({
            "type": "function",
            "function": {
                "name": capability.name,
                "description": f"{capability.display_name}：{capability.description} 只读。",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "question": {
                            "type": "string",
                            "description": "结合会话上下文补全后的具体查询目标。",
                        }
                    },
                },
            },
        })
    return specs


def tool_display_name(name: str) -> str:
    if name in TOOL_DISPLAY_NAMES:
        return TOOL_DISPLAY_NAMES[name]
    if name.startswith("inspect_") and name.endswith("_business_schema"):
        return "读取业务数据结构"
    if name.startswith("plan_") and name.endswith("_readonly_sql"):
        return "规划只读数据查询"
    if name.startswith("execute_") and name.endswith("_readonly_sql"):
        return "执行只读数据查询"
    if name.startswith("query_") and name.endswith("_docker_logs"):
        return "检查服务运行日志"
    return "执行只读工具"

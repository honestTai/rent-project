from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Any


PROJECT_KNOWLEDGE: dict[str, dict[str, Any]] = {
    "alipay": {
        "label": "支付宝租赁",
        "rules": [
            "rent_order.created_at 是毫秒时间戳；按自然日或自然月查询时，要把日期边界换算为 startMs/endMs。",
            "订单数、单量默认统计 rent_order 行数；销售额、到账收益、已支付金额默认使用 rent_order.paid_amount / 100 转为元。",
            "paid_amount、total_amount、deposit、damage_fee、overdue_fee、remaining_deposit、first_period_amount、per_period_amount、last_period_amount、installment_plan.period_amount、rent_deposit_deduct_record.deduct_amount 等金额字段按分存储，展示元必须 / 100。",
            "installment_plan.order_id 关联 rent_order.order_id；installment_plan.status=1 表示已支付分期，可用于分期实收分析。",
            "rent_order.status 是本地内部状态，rent_order.alipay_status 是支付宝履约状态；回答时要说明使用哪个状态口径。",
            "order_operation_log 用于排查回调、人工操作和状态流转；rent_deposit_deduct_record 用于押金扣减、赔付、售后扣款；rent_order_return_record 用于归还寄回。",
            "手机号、地址、身份证、授权号、合同路径、完整订单号、请求/响应明细属于敏感数据，不进入联网搜索，不在结果中明文展示。",
        ],
        "tables": {
            "rent_order": "订单主表；order_id/order_no 是订单标识，goods_id/goods_title 是商品，created_at 是毫秒创建时间，paid_amount 是分，status/alipay_status 分别代表本地状态和支付宝状态。",
            "installment_plan": "分期计划；order_id 关联 rent_order，period_no 是期数，period_amount 是分，plan_pay_time 是计划支付时间，status=1 可作为已支付分期。",
            "order_operation_log": "订单操作台账；operation_type/operation_desc/status_before/status_after/fail_reason 可排查状态流转和失败原因。",
            "rent_deposit_deduct_record": "押金扣减记录；fee_type/reason_code/deduct_amount/status/aftersale_status 可排查扣款和售后赔付。",
            "rent_order_return_record": "归还寄回记录；return_type/express_no/status/fail_reason/submitted_at 可排查归还链路。",
        },
    },
    "rental": {
        "label": "设备租赁",
        "rules": [
            "订单主表名是 `order`，属于 MySQL 关键字，SQL 中必须用反引号。",
            "sendTime 是寄出或订单开始口径；harvestTime/backTime 是收货或结束口径；runt 是租金收入。",
            "goodsId 关联 device.id；deviceName 是设备名称，deviceType 可做设备类别分析。",
            "订单数默认 COUNT(`order`)；收入默认 SUM(runt)，除非用户指定押金、成本或其他口径。",
        ],
        "tables": {
            "order": "设备租赁订单主表；sendTime/backTime/harvestTime 表示时间口径，goodsId 关联设备，runt 是租金收入。",
            "device": "设备档案；id/deviceName/deviceType/status 用于设备表现、库存和类型分析。",
            "inventory": "库存记录；deviceName/status/位置字段可用于库存状态分析。",
        },
    },
    "secondhand": {
        "label": "二手交易",
        "rules": [
            "second_device_out 是二手卖出订单表；buyDate 是成交日期，buyMoney 是销售额，costPrice 是成本，利润通常是 buyMoney - costPrice。",
            "second_device_out.into_id 关联 second_device_into.id；second_device_into.devName 是机型或设备名称，money 是入库成本。",
            "second_douyin_order 是抖音订单池；pay_amount 是平台支付金额，paid_at 是支付时间，platform_order_status 是抖音平台状态，sync_status 是本地同步状态。",
            "做销售、利润、排行时优先排除取消或无效记录；如果状态字段不清楚，要在回答中说明状态口径风险。",
        ],
        "tables": {
            "second_device_out": "二手卖出订单；buyDate/buyMoney/costPrice/status/can_id 可做销售和利润分析。",
            "second_device_into": "二手入库设备；id/devName/money 可补充机型、设备名称和成本。",
            "second_douyin_order": "抖音订单池；pay_amount/paid_at/platform_order_status/sync_status 用于抖音销售和同步分析。",
        },
    },
}


PROJECT_BUSINESS_MAP: dict[str, Any] = {
    "product": "无人机设备管理系统",
    "routing": [
        "/api/platform/** 与 /api/user/** 属于平台中台，覆盖登录、用户、RBAC、配置、系统日志、监控、通知和计划任务。",
        "/api/web/**、/api/rent/v1/**、/notify/** 属于支付宝租赁，覆盖后台订单、商品、分析、报表、小程序、回调、风控、押金和售后。",
        "/api/second/** 与 /api/secondRepair/** 属于二手交易，覆盖二手买入卖出、维修、分析、报表、抖音订单同步和卖家分佣。",
        "其余 /api/** 与 /images/** 属于设备租赁，覆盖设备、库存、租赁订单、维修、报表和订单来源。",
    ],
    "capabilities": [
        {
            "system": "alipay",
            "name": "支付宝租赁",
            "keywords": ["支付宝", "租赁订单", "小程序", "押金", "预授权", "租盾", "商品同步", "回调", "分期", "归还", "售后", "iOS审核"],
            "can_answer": [
                "租赁订单查询、状态流转、支付/分期、押金扣减、售后赔付、归还寄回、租盾和本地风控。",
                "商品档案和支付宝商品同步失败，经营分析、专题分析、周期报表。",
                "小程序登录、实名、下单、支付、合同预览、归还和回调链路。",
            ],
        },
        {
            "system": "rental",
            "name": "设备租赁",
            "keywords": ["设备租赁", "设备", "库存", "维修", "租金", "订单来源", "异常设备", "异常订单", "报表中心"],
            "can_answer": [
                "设备档案、设备分类、库存状态、报废、异常设备和维修记录。",
                "传统设备租赁订单、订单来源、租金收入、异常订单和报表中心。",
            ],
        },
        {
            "system": "secondhand",
            "name": "二手交易",
            "keywords": ["二手", "回收", "卖出", "买入", "维修", "抖音", "抖店", "同步", "分佣", "价格趋势", "滞销"],
            "can_answer": [
                "二手设备买入、卖出、库存、成本、利润、维修和价格趋势。",
                "抖音订单池、API日志、消息日志、同步任务、商品匹配和同步失败归因。",
                "卖家分佣、二手分析看板和周期报表。",
            ],
        },
        {
            "system": "platform",
            "name": "平台中台",
            "keywords": ["平台", "中台", "登录", "用户", "权限", "菜单", "按钮", "RBAC", "配置", "通知", "任务", "系统日志", "监控"],
            "can_answer": [
                "统一登录、用户、角色、菜单、按钮权限、平台配置、通知渠道、计划任务、系统日志和服务监控。",
                "平台目前主要通过只读接口和日志工具排查；没有专用只读业务库时，不编造 SQL 数据。",
            ],
        },
    ],
    "readonly_boundaries": [
        "Agent 只能查询只读数据库、只读接口、日志、本地知识和联网 MCP；不能调用保存、更新、删除、退款、扣款、发货、关闭、同步重试等写接口。",
        "内部业务数据优先查只读数据库或只读接口；所有公开信息联网查询统一走联网 MCP，仅用于官方文档、开放平台规则和背景资料，并由 LLM 汇总。",
        "手机号、地址、身份证、token、cookie、签名、授权号、合同路径、完整订单号和请求/响应明细默认不展示、不进入联网搜索。",
    ],
}


def knowledge_for_system(system: str) -> dict[str, Any]:
    return PROJECT_KNOWLEDGE.get(system, PROJECT_KNOWLEDGE["alipay"])


def project_business_map_text(max_chars: int = 3600) -> str:
    lines = [f"项目业务地图：{PROJECT_BUSINESS_MAP['product']}"]
    lines.append("网关与业务归属：")
    lines.extend(f"- {item}" for item in PROJECT_BUSINESS_MAP["routing"])
    lines.append("业务能力：")
    for capability in PROJECT_BUSINESS_MAP["capabilities"]:
        lines.append(
            f"- {capability['name']}({capability['system']})：关键词 "
            + "、".join(capability["keywords"][:10])
        )
        lines.extend(f"  - {item}" for item in capability["can_answer"])
    lines.append("只读边界：")
    lines.extend(f"- {item}" for item in PROJECT_BUSINESS_MAP["readonly_boundaries"])
    return "\n".join(lines)[:max_chars]


@lru_cache(maxsize=1)
def _load_equipment_business_skill() -> str:
    path = Path(__file__).with_name("equipment_business_skill.md")
    try:
        return path.read_text(encoding="utf-8")
    except OSError:
        return project_business_map_text(max_chars=6000)


def equipment_business_skill_text(max_chars: int = 9000) -> str:
    return _load_equipment_business_skill()[:max_chars]


def knowledge_text_for_system(system: str, max_chars: int = 2600) -> str:
    knowledge = knowledge_for_system(system)
    lines = [f"项目业务知识：{knowledge.get('label', system)}"]
    rules = knowledge.get("rules") or []
    if rules:
        lines.append("关键规则：")
        lines.extend(f"- {rule}" for rule in rules)
    tables = knowledge.get("tables") or {}
    if tables:
        lines.append("关键表字段：")
        lines.extend(f"- {name}：{description}" for name, description in tables.items())
    text = "\n".join(lines)
    return text[:max_chars]

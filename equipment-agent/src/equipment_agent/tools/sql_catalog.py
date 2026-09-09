from __future__ import annotations

from typing import Any


SQL_TABLE_CATALOG: dict[str, list[dict[str, Any]]] = {
    "alipay": [
        {
            "name": "rent_order",
            "description": "支付宝租赁订单主表；按 created_at 毫秒时间统计订单，goods_id/goods_title 表示商品或设备，alipay_status 表示支付宝履约状态。",
        },
        {
            "name": "installment_plan",
            "description": "支付宝租赁分期计划；order_id 关联 rent_order.order_id，status=1 表示已支付分期，period_amount 是实际租金支付金额。",
        },
        {
            "name": "goods",
            "description": "支付宝租赁商品或设备档案；可用于补充商品名称、上下架状态和价格配置。",
        },
        {
            "name": "order_operation_log",
            "description": "支付宝后台订单操作台账；用于定位订单状态流转、回调、人工操作和异常原因。",
        },
        {
            "name": "alipay_goods_sync_log",
            "description": "支付宝商品同步日志；用于分析商品同步成功率、失败原因和最近同步时间。",
        },
        {
            "name": "rent_deposit_deduct_record",
            "description": "支付宝押金扣减记录；用于只读排查扣款、赔付和售后扣减状态。",
        },
        {
            "name": "rent_order_return_record",
            "description": "支付宝租赁寄回记录；用于排查归还物流、寄回时间和售后进度。",
        },
        {
            "name": "user",
            "description": "支付宝租赁用户表；仅用于新增用户数、用户总量等聚合分析，手机号、身份证、地址、openid、uuid 等敏感字段不能明文查询或展示。",
        },
    ],
    "rental": [
        {
            "name": "order",
            "description": "设备租赁订单主表；sendTime 是寄出时间，backTime/harvestTime 是结束或收货时间，goodsId 关联设备，runt 是租金收入。",
        },
        {
            "name": "device",
            "description": "设备档案；id 关联 order.goodsId，deviceName 是设备名称，deviceType/状态字段可辅助分析库存和设备表现。",
        },
        {
            "name": "deviceclass",
            "description": "设备分类；用于按类别汇总设备、库存和租赁表现。",
        },
        {
            "name": "ordersource",
            "description": "订单来源；用于按渠道、来源统计订单量和租金收入。",
        },
        {
            "name": "inventory",
            "description": "设备库存记录；用于查询库存位置、状态和异常库存。",
        },
        {
            "name": "problemlog",
            "description": "设备问题记录；用于分析设备故障、异常和维修线索。",
        },
        {
            "name": "repair",
            "description": "设备维修记录；用于分析维修频次、维修成本和设备可靠性。",
        },
    ],
    "secondhand": [
        {
            "name": "second_device_out",
            "description": "二手卖出订单表；buyDate 是成交日期，buyMoney 是销售额，costPrice 是实际成本，status/can_id 可排除取消记录。",
        },
        {
            "name": "second_device_into",
            "description": "二手入库设备表；id 关联 second_device_out.into_id，devName 是机型或设备名称，money 是入库成本。",
        },
        {
            "name": "rent",
            "description": "二手租赁或暂存记录；关联 second_device_into，可用于二手设备在租、库存和周转分析。",
        },
        {
            "name": "dict",
            "description": "二手字典表；可用于机器名称、类别或层级字典映射。",
        },
        {
            "name": "second_douyin_order",
            "description": "二手抖音订单池；用于分析抖音订单同步、平台订单状态和导入状态。",
        },
        {
            "name": "second_douyin_api_log",
            "description": "二手抖音开放 API 调用日志；用于排查接口错误、响应耗时和同步失败。",
        },
        {
            "name": "second_douyin_message_log",
            "description": "二手抖音消息回调日志；用于排查消息通知、验签和订单变更事件。",
        },
        {
            "name": "second_douyin_sync_task",
            "description": "二手抖音同步任务记录；用于查看定时同步任务状态和失败原因。",
        },
    ],
}


def catalog_for_system(system: str) -> list[dict[str, Any]]:
    return SQL_TABLE_CATALOG.get(system, SQL_TABLE_CATALOG["alipay"])


def catalog_table_names(system: str) -> list[str]:
    return [item["name"] for item in catalog_for_system(system)]

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, datetime, time, timedelta, timezone
from typing import Any
from zoneinfo import ZoneInfo


@dataclass(frozen=True)
class DevicePerformanceQuery:
    system: str
    title: str
    metric_label: str
    score_basis: str
    tables: list[str]
    sql: str
    params: dict[str, Any]
    chart_value_keys: list[str]


def build_device_performance_query(
    system: str,
    start_date: date,
    end_date: date,
    limit: int,
    metric_mode: str = "best",
) -> DevicePerformanceQuery:
    limit = max(3, min(int(limit or 10), 20))
    if system == "rental":
        return _rental_query(start_date, end_date, limit, metric_mode)
    if system == "secondhand":
        return _secondhand_query(start_date, end_date, limit, metric_mode)
    return _alipay_query(start_date, end_date, limit, metric_mode)


def rank_device_rows(rows: list[dict[str, Any]], system: str, metric_mode: str = "best") -> list[dict[str, Any]]:
    if not rows:
        return []
    if metric_mode == "orders":
        primary_key = "orderCount"
    elif system == "secondhand":
        primary_key = "totalProfit"
    else:
        primary_key = "revenueYuan"
    order_key = "orderCount"
    avg_key = "profitRate" if system == "secondhand" else "avgRevenueYuan"
    primary_max = max(_number(row.get(primary_key)) for row in rows) or 1.0
    order_max = max(_number(row.get(order_key)) for row in rows) or 1.0
    avg_max = max(_number(row.get(avg_key)) for row in rows) or 1.0
    ranked = []
    for row in rows:
        primary_score = max(_number(row.get(primary_key)), 0.0) / primary_max
        order_score = max(_number(row.get(order_key)), 0.0) / order_max
        avg_score = max(_number(row.get(avg_key)), 0.0) / avg_max
        if metric_mode == "orders":
            score = order_score * 70 + primary_score * 20 + avg_score * 10
        elif metric_mode in {"revenue", "profit"}:
            score = primary_score * 70 + order_score * 20 + avg_score * 10
        else:
            score = primary_score * 55 + order_score * 25 + avg_score * 20
        next_row = dict(row)
        next_row["score"] = round(score, 2)
        ranked.append(next_row)
    ranked.sort(key=lambda item: (_number(item.get("score")), _number(item.get(primary_key)), _number(item.get(order_key))), reverse=True)
    for index, row in enumerate(ranked, start=1):
        row["rank"] = index
    return ranked


def metric_mode_from_message(message: str) -> str:
    lower = (message or "").lower()
    if any(word in message for word in ["单量", "订单最多", "最多订单", "销量", "卖得最好", "租得最多"]):
        return "orders"
    if any(word in message for word in ["利润", "毛利", "赚钱", "最赚"]):
        return "profit"
    if any(word in message for word in ["收入", "营收", "金额", "收益"]):
        return "revenue"
    if "top" in lower or "rank" in lower:
        return "best"
    return "best"


def _alipay_query(start_date: date, end_date: date, limit: int, metric_mode: str) -> DevicePerformanceQuery:
    order_by = _order_by(metric_mode, revenue="revenueYuan", profit="revenueYuan")
    sql = f"""
SELECT
  COALESCE(NULLIF(TRIM(o.goods_title), ''), CONCAT('商品#', o.goods_id)) AS deviceName,
  o.goods_id AS deviceId,
  COUNT(1) AS orderCount,
  ROUND(IFNULL(SUM(
    CASE
      WHEN UPPER(COALESCE(NULLIF(o.alipay_status, ''), 'FINISHED')) NOT IN ('CLOSED', 'PENDING_CANCEL', 'CANCELLED', 'REFUNDING', 'REFUNDED')
      THEN IFNULL(p.paidYuan, 0)
      ELSE 0
    END
  ), 0), 2) AS revenueYuan,
  ROUND(IFNULL(AVG(
    CASE
      WHEN UPPER(COALESCE(NULLIF(o.alipay_status, ''), 'FINISHED')) NOT IN ('CLOSED', 'PENDING_CANCEL', 'CANCELLED', 'REFUNDING', 'REFUNDED')
      THEN IFNULL(p.paidYuan, 0)
      ELSE 0
    END
  ), 0), 2) AS avgRevenueYuan,
  SUM(
    CASE
      WHEN UPPER(COALESCE(NULLIF(o.alipay_status, ''), 'FINISHED')) NOT IN ('CLOSED', 'PENDING_CANCEL', 'CANCELLED', 'REFUNDING', 'REFUNDED')
      THEN GREATEST(IFNULL(o.rent_days, 0), 0)
      ELSE 0
    END
  ) AS rentDays,
  DATE_FORMAT(MAX(FROM_UNIXTIME(o.created_at / 1000)), '%Y-%m-%d %H:%i:%s') AS lastOrderAt
FROM rent_order o
LEFT JOIN (
  SELECT
    order_id,
    SUM(CAST(NULLIF(period_amount, '') AS DECIMAL(12, 2))) AS paidYuan
  FROM installment_plan
  WHERE status = 1
  GROUP BY order_id
) p ON p.order_id = o.order_id
WHERE o.created_at >= :start_ms
  AND o.created_at <= :end_ms
GROUP BY COALESCE(NULLIF(TRIM(o.goods_title), ''), CONCAT('商品#', o.goods_id)), o.goods_id
ORDER BY {order_by}, orderCount DESC
LIMIT :limit
"""
    return DevicePerformanceQuery(
        system="alipay",
        title="支付宝租赁设备表现排行",
        metric_label="确认收入",
        score_basis="按实际已支付分期金额、订单数、平均收入综合评分；关闭、取消、退款中订单不确认收入。",
        tables=["rent_order", "installment_plan", "goods"],
        sql=sql,
        params={
            "start_ms": int(_start_datetime(start_date).timestamp() * 1000),
            "end_ms": int(_end_datetime(end_date).timestamp() * 1000),
            "limit": limit,
        },
        chart_value_keys=["score", "revenueYuan", "orderCount"],
    )


def _rental_query(start_date: date, end_date: date, limit: int, metric_mode: str) -> DevicePerformanceQuery:
    order_by = _order_by(metric_mode, revenue="revenueYuan", profit="revenueYuan")
    sql = f"""
SELECT
  COALESCE(NULLIF(TRIM(d.deviceName), ''), CONCAT('设备#', o.goodsId)) AS deviceName,
  o.goodsId AS deviceId,
  COUNT(1) AS orderCount,
  ROUND(IFNULL(SUM(IFNULL(o.runt, 0)), 0), 2) AS revenueYuan,
  ROUND(IFNULL(AVG(IFNULL(o.runt, 0)), 0), 2) AS avgRevenueYuan,
  ROUND(IFNULL(SUM(GREATEST(DATEDIFF(IFNULL(o.backTime, o.harvestTime), o.sendTime), 0)), 0), 0) AS rentDays,
  DATE_FORMAT(MAX(o.sendTime), '%Y-%m-%d %H:%i:%s') AS lastOrderAt
FROM `order` o
LEFT JOIN device d ON o.goodsId = d.id
WHERE o.sendTime >= :start_at
  AND o.sendTime <= :end_at
GROUP BY COALESCE(NULLIF(TRIM(d.deviceName), ''), CONCAT('设备#', o.goodsId)), o.goodsId
ORDER BY {order_by}, orderCount DESC
LIMIT :limit
"""
    return DevicePerformanceQuery(
        system="rental",
        title="设备租赁设备表现排行",
        metric_label="租金收入",
        score_basis="按租金收入、订单数、平均租金综合评分。",
        tables=["order", "device"],
        sql=sql,
        params={"start_at": _start_datetime(start_date), "end_at": _end_datetime(end_date), "limit": limit},
        chart_value_keys=["score", "revenueYuan", "orderCount"],
    )


def _secondhand_query(start_date: date, end_date: date, limit: int, metric_mode: str) -> DevicePerformanceQuery:
    order_by = _order_by(metric_mode, revenue="totalSellAmount", profit="totalProfit")
    sql = f"""
SELECT
  COALESCE(NULLIF(TRIM(i.devName), ''), '未知机型') AS deviceName,
  COUNT(1) AS orderCount,
  ROUND(IFNULL(SUM(IFNULL(o.buyMoney, 0)), 0), 2) AS totalSellAmount,
  ROUND(IFNULL(SUM(IFNULL(o.buyMoney, 0) - IFNULL(o.costPrice, IFNULL(i.money, 0))), 0), 2) AS totalProfit,
  ROUND(IFNULL(AVG(IFNULL(o.buyMoney, 0) - IFNULL(o.costPrice, IFNULL(i.money, 0))), 0), 2) AS averageProfit,
  ROUND(IFNULL(
    SUM(IFNULL(o.buyMoney, 0) - IFNULL(o.costPrice, IFNULL(i.money, 0))) / NULLIF(SUM(IFNULL(o.buyMoney, 0)), 0),
    0
  ) * 100, 2) AS profitRate,
  ROUND(IFNULL(AVG(IFNULL(o.buyDay, 0)), 0), 2) AS avgSellDays,
  DATE_FORMAT(MAX(o.buyDate), '%Y-%m-%d') AS lastOrderAt
FROM second_device_out o
LEFT JOIN second_device_into i ON o.into_id = i.id
WHERE o.can_id IS NULL
  AND o.status <> 4
  AND o.buyDate >= :start_date
  AND o.buyDate <= :end_date
GROUP BY COALESCE(NULLIF(TRIM(i.devName), ''), '未知机型')
ORDER BY {order_by}, orderCount DESC
LIMIT :limit
"""
    return DevicePerformanceQuery(
        system="secondhand",
        title="二手交易设备利润排行",
        metric_label="利润",
        score_basis="按利润、订单数、利润率综合评分；排除取消购买记录。",
        tables=["second_device_out", "second_device_into"],
        sql=sql,
        params={"start_date": start_date.isoformat(), "end_date": end_date.isoformat(), "limit": limit},
        chart_value_keys=["score", "totalProfit", "orderCount"],
    )


def _order_by(metric_mode: str, revenue: str, profit: str) -> str:
    if metric_mode == "orders":
        return "orderCount DESC"
    if metric_mode == "profit":
        return f"{profit} DESC"
    return f"{revenue} DESC"


def _start_datetime(value: date) -> datetime:
    return datetime.combine(value, time.min, _china_tz())


def _end_datetime(value: date) -> datetime:
    return datetime.combine(value, time.max.replace(microsecond=0), _china_tz())


def _china_tz():
    try:
        return ZoneInfo("Asia/Shanghai")
    except Exception:
        return timezone(timedelta(hours=8))


def _number(value: Any) -> float:
    try:
        return float(value or 0)
    except (TypeError, ValueError):
        return 0.0

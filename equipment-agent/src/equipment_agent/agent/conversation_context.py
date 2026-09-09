from __future__ import annotations

import re
from typing import Any


_TIME_WORDS = ("大前天", "前天", "昨天", "今天", "明天", "后天", "本周", "上周", "本月", "上月", "今年", "去年")
_WEATHER_TOPIC = "天气"
_WEATHER_LOCATION_QUESTION = "你想查询哪个城市的天气？"
_WEATHER_TERMS = ("天气", "气温", "温度", "多少度", "几度", "冷不冷", "热不热")
_NON_WEATHER_TEMPERATURE_HINTS = ("设备", "机器", "电机", "电池", "CPU", "cpu", "传感器", "机柜")
_VAGUE_REQUESTS = {
    "查",
    "查一下",
    "查询",
    "帮我查",
    "帮我查一下",
    "看一下",
    "看看",
    "分析一下",
    "这个呢",
    "那个呢",
}


def resolve_turn_context(
    message: str,
    previous: dict[str, object] | None,
    *,
    system: str,
) -> dict[str, object]:
    """Merge explicit turn slots over conversation slots and identify hard missing inputs."""

    text = (message or "").strip()
    prior = dict(previous or {})
    snapshot: dict[str, Any] = {
        key: value
        for key, value in prior.items()
        if value not in (None, "", [], {})
    }
    snapshot["system"] = _explicit_system(text) or system or str(snapshot.get("system") or "alipay")

    explicit_time = _extract_time_range(text)
    if explicit_time:
        snapshot["timeRange"] = explicit_time

    awaiting_weather_location = (
        prior.get("topic") == _WEATHER_TOPIC
        and prior.get("pendingQuestion") == _WEATHER_LOCATION_QUESTION
    )
    weather_turn = _looks_like_weather_request(text) or (
        snapshot.get("topic") == _WEATHER_TOPIC
        and (awaiting_weather_location or _looks_like_followup(text))
    )
    if weather_turn:
        snapshot["topic"] = _WEATHER_TOPIC
        location = _extract_weather_location(text, allow_bare=awaiting_weather_location)
        if location:
            snapshot["location"] = location

    questions: list[dict[str, Any]] = []
    if weather_turn and not str(snapshot.get("location") or "").strip():
        questions.append({
            "question": _WEATHER_LOCATION_QUESTION,
            "description": "补充城市后，我会联网查询对应日期的天气并汇总来源。",
        })
    elif _normalized_request(text) in _VAGUE_REQUESTS and not snapshot.get("topic"):
        questions.append({
            "question": "你想查询什么内容？",
            "description": "请补充对象、时间范围或想了解的问题，我再决定需要哪些工具。",
        })

    if questions:
        snapshot["pendingQuestion"] = questions[0]["question"]
    else:
        snapshot.pop("pendingQuestion", None)

    resolved_message = text
    if weather_turn and snapshot.get("location") and not questions:
        resolved_message = "".join(
            str(value)
            for value in [snapshot.get("timeRange"), snapshot.get("location"), _WEATHER_TOPIC]
            if value
        )

    return {
        "snapshot": snapshot,
        "needsClarification": bool(questions),
        "questions": questions,
        "resolvedMessage": resolved_message,
    }


def context_for_prompt(snapshot: dict[str, object] | None) -> str:
    """Return a compact Chinese context description for planner and reflection prompts."""

    data = snapshot or {}
    labels = {
        "topic": "当前主题",
        "system": "当前系统",
        "timeRange": "时间范围",
        "location": "城市/地区",
        "entity": "业务对象",
        "metric": "分析指标",
        "searchQuery": "最近搜索主题",
        "pendingQuestion": "待确认问题",
    }
    parts = [
        f"{label}：{data[key]}"
        for key, label in labels.items()
        if data.get(key) not in (None, "", [], {})
    ]
    return "\n".join(parts) if parts else "- 无已确认上下文"


def _extract_time_range(text: str) -> str:
    days = re.search(r"(?:最近|近)\s*(\d{1,3})\s*天", text)
    if days:
        return f"最近 {int(days.group(1))} 天"
    for word in _TIME_WORDS:
        if word in text:
            return word
    return ""


def _extract_weather_location(text: str, *, allow_bare: bool = False) -> str:
    change = re.search(
        r"换成\s*([\u4e00-\u9fff]{2,10}?)(?:市)?(?:大前天|前天|昨天|今天|明天|后天)?(?:的)?(?:天气)?(?:怎么样|如何)?[？?。！!]*$",
        text,
    )
    if change:
        return _clean_location(change.group(1))

    normalized = text.strip()
    if allow_bare:
        bare = re.fullmatch(r"([\u4e00-\u9fff]{2,10}?)(?:市)?[？?。！!]*", normalized)
        if bare:
            return _clean_location(bare.group(1))

    if not _looks_like_weather_request(text):
        short_followup = re.fullmatch(r"([\u4e00-\u9fff]{2,10}?)(?:呢|怎么样|如何)[？?。！!]*", text.strip())
        if short_followup:
            candidate = short_followup.group(1)
            generic_words = ("然后", "继续", "再查", "那个", "这个")
            if not any(word in candidate for word in (*_TIME_WORDS, *generic_words)):
                return _clean_location(candidate)
        return ""
    prefix = re.sub(r"[，,。.!！？?\s]", "", text)
    prefix = re.sub(r"请|麻烦|帮我|给我|查询|查一下|查|看一下|看看|想知道|怎么样|如何|的|现在|当地|这边", "", prefix)
    for word in _TIME_WORDS:
        prefix = prefix.replace(word, "")
    prefix = re.sub(r"(?:最近|近)\d{1,3}天", "", prefix)
    for word in _WEATHER_TERMS:
        prefix = prefix.replace(word, "")
    prefix = re.sub(r"(?:是多少|多少|几|呀|啊|呢|吧)+$", "", prefix)
    return _clean_location(prefix)


def _clean_location(value: str) -> str:
    location = (value or "").strip()
    if location.endswith("市") and len(location) > 2:
        location = location[:-1]
    if not 2 <= len(location) <= 10:
        return ""
    if location in {
        "哪里", "哪个城市", "所在城市", "当地", "这边", "这里", "那边",
        "不知道", "不清楚", "随便", "没有", "算了", "温度", "气温", "天气",
    }:
        return ""
    return location


def _looks_like_weather_request(text: str) -> bool:
    if any(word in text for word in ("天气", "气温")):
        return True
    if any(word in text for word in _NON_WEATHER_TEMPERATURE_HINTS):
        return False
    return any(word in text for word in _WEATHER_TERMS[2:])


def _explicit_system(text: str) -> str:
    if any(word in text for word in ["二手", "抖音", "抖店"]):
        return "secondhand"
    if any(word in text for word in ["支付宝", "租物"]):
        return "alipay"
    if any(word in text for word in ["设备租赁", "租赁管理"]):
        return "rental"
    return ""


def _looks_like_followup(text: str) -> bool:
    compact = _normalized_request(text)
    return bool(
        compact.endswith("呢")
        or any(word in compact for word in ["换成", "那", "然后", "再查", "继续", "明天", "后天", "昨天"])
    )


def _normalized_request(text: str) -> str:
    return re.sub(r"[，,。.!！？?\s]", "", text or "")

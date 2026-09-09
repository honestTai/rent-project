from equipment_agent.agent.conversation_context import resolve_turn_context


def test_weather_without_city_requires_clarification():
    result = resolve_turn_context("今天天气怎么样", {}, system="alipay")

    assert result["needsClarification"] is True
    assert result["questions"][0]["question"] == "你想查询哪个城市的天气？"


def test_temperature_without_city_requires_weather_clarification():
    result = resolve_turn_context("今天多少度呀", {}, system="alipay")

    assert result["needsClarification"] is True
    assert result["snapshot"]["topic"] == "天气"
    assert result["snapshot"]["timeRange"] == "今天"
    assert result["questions"][0]["question"] == "你想查询哪个城市的天气？"


def test_bare_city_answer_fills_pending_weather_location():
    result = resolve_turn_context(
        "成都",
        {
            "topic": "天气",
            "timeRange": "今天",
            "pendingQuestion": "你想查询哪个城市的天气？",
        },
        system="alipay",
    )

    assert result["needsClarification"] is False
    assert result["snapshot"]["location"] == "成都"
    assert result["resolvedMessage"] == "今天成都天气"


def test_device_temperature_does_not_enter_weather_context():
    result = resolve_turn_context("设备电池温度多少度", {}, system="rental")

    assert result["needsClarification"] is False
    assert result["snapshot"].get("topic") != "天气"


def test_weather_followup_inherits_confirmed_city():
    result = resolve_turn_context(
        "那明天呢",
        {"topic": "天气", "location": "杭州", "timeRange": "今天"},
        system="alipay",
    )

    assert result["needsClarification"] is False
    assert result["snapshot"]["location"] == "杭州"
    assert result["snapshot"]["timeRange"] == "明天"


def test_explicit_turn_value_overrides_old_context():
    result = resolve_turn_context(
        "换成上海今天的天气",
        {"topic": "天气", "location": "杭州", "timeRange": "明天"},
        system="alipay",
    )

    assert result["snapshot"]["location"] == "上海"
    assert result["snapshot"]["timeRange"] == "今天"


def test_short_weather_followup_city_overrides_old_context():
    result = resolve_turn_context(
        "上海呢",
        {"topic": "天气", "location": "杭州", "timeRange": "明天"},
        system="alipay",
    )

    assert result["snapshot"]["location"] == "上海"
    assert result["snapshot"]["timeRange"] == "明天"
    assert result["resolvedMessage"] == "明天上海天气"


def test_bare_lookup_requires_clarification_before_tools():
    result = resolve_turn_context("查一下", {}, system="secondhand")

    assert result["needsClarification"] is True
    assert result["questions"][0]["question"] == "你想查询什么内容？"


def test_clear_general_chat_does_not_require_clarification():
    result = resolve_turn_context("帮我把这句话写得自然一点", {}, system="rental")

    assert result["needsClarification"] is False
    assert result["snapshot"]["system"] == "rental"

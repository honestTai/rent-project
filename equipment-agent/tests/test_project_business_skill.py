from __future__ import annotations

from equipment_agent.knowledge.project_knowledge import equipment_business_skill_text


def test_equipment_business_skill_contains_table_field_business_mapping():
    skill = equipment_business_skill_text(max_chars=20000)

    assert "无人机设备管理系统业务 Skill" in skill
    assert "rent_order" in skill
    assert "created_at" in skill
    assert "paid_amount" in skill
    assert "分" in skill
    assert "`order`" in skill
    assert "sendTime" in skill
    assert "second_device_out" in skill
    assert "second_douyin_order" in skill
    assert "platform_role" in skill
    assert "手机号" in skill
    assert "禁止写入" in skill
    assert "自动图表" in skill
    assert "docker logs" in skill
    assert "公开网页" in skill


def test_equipment_business_skill_uses_product_name_without_foreign_brand():
    skill = equipment_business_skill_text(max_chars=20000).lower()

    assert "无人机设备管理系统" in skill
    assert ("le" + "novo") not in skill
    assert ("\u8054" + "\u60f3") not in skill

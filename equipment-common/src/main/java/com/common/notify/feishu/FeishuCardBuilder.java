package com.common.notify.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书卡片构造器。
 * 当前统一输出“摘要字段 + 按钮”的轻量卡片，便于报表通知与订单生命周期通知复用。
 */
public class FeishuCardBuilder {

    private final ObjectMapper objectMapper;

    public FeishuCardBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildPeriodicReportCard(String title,
                                          Map<String, String> basicFields,
                                          Map<String, String> metricFields,
                                          String actionText,
                                          String actionUrl) {
        return toJson(buildCard(title, "blue", basicFields, metricFields, actionText, actionUrl));
    }

    public String buildLifecycleCard(String title,
                                     Map<String, String> basicFields,
                                     Map<String, String> metricFields,
                                     String primaryActionText,
                                     String primaryActionUrl,
                                     String secondaryActionText,
                                     String secondaryActionUrl) {
        Map<String, Object> card = buildCard(title, "wathet", basicFields, metricFields, primaryActionText, primaryActionUrl);
        addSecondaryAction(card, secondaryActionText, secondaryActionUrl);
        return toJson(card);
    }

    public String buildExceptionCard(String title,
                                     Map<String, String> basicFields,
                                     String actionText,
                                     String actionUrl) {
        return toJson(buildCard(title, "red", basicFields, null, actionText, actionUrl));
    }

    private Map<String, Object> buildCard(String title,
                                          String template,
                                          Map<String, String> basicFields,
                                          Map<String, String> metricFields,
                                          String actionText,
                                          String actionUrl) {
        Map<String, Object> card = new LinkedHashMap<>();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("wide_screen_mode", true);
        card.put("config", config);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("template", template);
        Map<String, Object> headerTitle = new LinkedHashMap<>();
        headerTitle.put("tag", "plain_text");
        headerTitle.put("content", title);
        header.put("title", headerTitle);
        card.put("header", header);

        List<Object> elements = new ArrayList<>();
        if (basicFields != null && !basicFields.isEmpty()) {
            elements.add(buildFieldBlock(basicFields));
        }
        if (metricFields != null && !metricFields.isEmpty()) {
            elements.add(buildMarkdownBlock(metricFields));
        }
        if (hasText(actionText) && hasText(actionUrl)) {
            elements.add(buildActionBlock(actionText, actionUrl));
        }
        card.put("elements", elements);
        return card;
    }

    private Map<String, Object> buildFieldBlock(Map<String, String> fields) {
        List<Object> fieldItems = new ArrayList<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            Map<String, Object> field = new LinkedHashMap<>();
            field.put("is_short", true);
            Map<String, Object> text = new LinkedHashMap<>();
            text.put("tag", "lark_md");
            text.put("content", "**" + safe(entry.getKey()) + "**\n" + safe(entry.getValue()));
            field.put("text", text);
            fieldItems.add(field);
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("tag", "div");
        block.put("fields", fieldItems);
        return block;
    }

    private Map<String, Object> buildMarkdownBlock(Map<String, String> fields) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("- **").append(safe(entry.getKey())).append("**: ").append(safe(entry.getValue()));
        }
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("tag", "lark_md");
        text.put("content", builder.toString());
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("tag", "div");
        block.put("text", text);
        return block;
    }

    private Map<String, Object> buildActionBlock(String actionText, String actionUrl) {
        List<Object> actions = new ArrayList<>();
        actions.add(buildButton("primary", actionText, actionUrl));
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("tag", "action");
        block.put("actions", actions);
        return block;
    }

    @SuppressWarnings("unchecked")
    private void addSecondaryAction(Map<String, Object> card,
                                    String secondaryActionText,
                                    String secondaryActionUrl) {
        if (!hasText(secondaryActionText) || !hasText(secondaryActionUrl)) {
            return;
        }
        List<Object> elements = (List<Object>) card.get("elements");
        if (elements == null || elements.isEmpty()) {
            return;
        }
        Object lastElement = elements.get(elements.size() - 1);
        if (!(lastElement instanceof Map)) {
            return;
        }
        Map<String, Object> actionBlock = (Map<String, Object>) lastElement;
        if (!"action".equals(actionBlock.get("tag"))) {
            return;
        }
        List<Object> actions = (List<Object>) actionBlock.get("actions");
        actions.add(buildButton("default", secondaryActionText, secondaryActionUrl));
    }

    private Map<String, Object> buildButton(String buttonType, String textValue, String url) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("tag", "plain_text");
        text.put("content", textValue);
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("tag", "button");
        button.put("type", buttonType);
        button.put("text", text);
        button.put("url", url);
        return button;
    }

    private String toJson(Map<String, Object> card) {
        try {
            return objectMapper.writeValueAsString(card);
        } catch (Exception e) {
            throw new IllegalStateException("构造飞书卡片失败", e);
        }
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

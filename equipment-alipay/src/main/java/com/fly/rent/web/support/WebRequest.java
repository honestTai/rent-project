package com.fly.rent.web.support;

import com.common.Util.UserInfo.UserContextHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;

/**
 * Web 兼容层请求参数封装。
 * 将前端 POST JSON body（Map）包装为类型安全的取值工具，方便各 Service 统一解析参数。
 */
public class WebRequest {

    private final Map<String, Object> body;
    private final String operator;

    /** 私有构造，通过 {@link #of(Map)} 工厂方法创建实例。 */
    private WebRequest(Map<String, Object> body) {
        this.body = body == null ? Collections.emptyMap() : body;
        this.operator = resolveOperator(this.body);
    }

    /** 从请求体 Map 创建实例，body 为 null 时返回空 WebRequest。 */
    public static WebRequest of(Map<String, Object> body) {
        return new WebRequest(body);
    }

    /** 获取原始 Map。 */
    public Map<String, Object> raw() {
        return body;
    }

    /** 获取当前后台操作人。 */
    public String operator() {
        return operator;
    }

    /** 取字符串值，不存在返回 null。 */
    public String text(String key) {
        Object val = body.get(key);
        return val == null ? null : val.toString();
    }

    /** 取字符串值，不存在时抛异常。 */
    public String requiredText(String key) {
        String val = text(key);
        if (val == null || val.trim().isEmpty()) {
            throw new IllegalArgumentException(key + " 不能为空");
        }
        return val.trim();
    }

    /** 是否含有非空字符串值。 */
    public boolean hasText(String key) {
        String val = text(key);
        return val != null && !val.trim().isEmpty();
    }

    /** 取 Integer，不存在返回 null。 */
    public Integer integer(String key) {
        Object val = body.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    /** 取 int，不存在返回默认值。 */
    public int intValue(String key, int defaultValue) {
        Integer val = integer(key);
        return val == null ? defaultValue : val;
    }

    /** 取 Long，不存在返回 null。 */
    public Long longValue(String key) {
        Object val = body.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    /** 取 Boolean，不存在返回 null。 */
    public Boolean bool(String key) {
        Object val = body.get(key);
        if (val == null) return null;
        if (val instanceof Boolean) return (Boolean) val;
        return Boolean.parseBoolean(val.toString());
    }

    /** 取分页页码，默认 1。 */
    public int page() {
        int p = intValue("page", 1);
        return Math.max(p, 1);
    }

    /** 取分页大小，默认 10，上限 200。 */
    public int limit() {
        int l = intValue("limit", 10);
        return Math.min(Math.max(l, 1), 200);
    }

    private String resolveOperator(Map<String, Object> body) {
        String bodyOperator = firstText(textValue(body.get("operatorName")),
                textValue(body.get("operator")),
                textValue(body.get("userName")));
        if (StringUtils.hasText(bodyOperator)) {
            return bodyOperator;
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        return firstText(request.getHeader(UserContextHeaders.USER_NAME),
                request.getHeader(UserContextHeaders.USER_ID));
    }

    private String textValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}

package com.fly.rent.web.support;

import com.fly.rent.entity.Result;

import java.util.List;
import java.util.Map;

/**
 * Web 兼容层控制器抽象基类。
 * 为所有 /api/web/ 接口提供统一的请求解析与响应封装能力。
 */
public abstract class AbstractWebController {

    /** 将请求体封装为 WebRequest。 */
    protected WebRequest request(Map<String, Object> body) {
        return WebRequest.of(body);
    }

    /** 构建成功响应。 */
    protected Result success(Object data) {
        return WebResponseUtil.success(data);
    }

    /** 构建无数据成功响应。 */
    protected Result success() {
        return WebResponseUtil.success();
    }

    /** 构建分页响应。 */
    protected Result page(List<?> records, long total) {
        return WebResponseUtil.page(records, total);
    }
}

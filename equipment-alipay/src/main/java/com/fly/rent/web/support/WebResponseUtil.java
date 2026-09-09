package com.fly.rent.web.support;

import com.fly.rent.entity.Result;
import org.slf4j.MDC;

import java.util.List;

/**
 * Web 兼容层统一响应构建工具。
 * 前端期望格式：{code:0, msg:"success", data:..., count:...}。
 */
public final class WebResponseUtil {

    private WebResponseUtil() {}

    /** 带数据的成功响应。 */
    public static Result success(Object data) {
        Result r = new Result();
        r.setCode(0);
        r.setMsg("success");
        r.setData(data);
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /** 无数据的成功响应。 */
    public static Result success() {
        return success(null);
    }

    /** 分页成功响应。 */
    public static Result page(List<?> records, long total) {
        Result r = new Result();
        r.setCode(0);
        r.setMsg("success");
        r.setData(records);
        r.setCount((int) total);
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /** 错误响应。 */
    public static Result error(int code, String msg) {
        Result r = new Result();
        r.setCode(code);
        r.setMsg(msg);
        r.setMessage(msg);
        r.setTraceId(MDC.get("traceId"));
        return r;
    }
}

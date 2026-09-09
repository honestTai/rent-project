package com.fly.rent.support.util;

import com.fly.rent.entity.Result;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一响应组装工具。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public final class ResultUtil {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    private ResultUtil() {
    }

    /**
     * 返回无数据的成功结果。
     * @return 成功结果
     */
    public static Result success() {
        Result result = new Result();
        result.setCode(0);
        result.setMsg("OK");
        result.setTraceId(MDC.get(TRACE_ID_MDC_KEY));
        return result;
    }

    /**
     * 返回带数据的成功结果。
     * @param data 数据
     * @return 成功结果
     */
    public static Result success(Object data) {
        Result result = new Result();
        result.setCode(0);
        result.setMsg("OK");
        result.setData(data);
        result.setTraceId(MDC.get(TRACE_ID_MDC_KEY));
        return result;
    }

    /**
     * 返回带自定义消息的成功结果。
     * @param data 数据
     * @param msg 消息
     * @return 成功结果
     */
    public static Result success(Object data, String msg) {
        Result result = new Result();
        result.setCode(0);
        result.setMsg(msg);
        result.setData(data);
        result.setTraceId(MDC.get(TRACE_ID_MDC_KEY));
        return result;
    }

    /**
     * 返回带自定义状态码的成功结果。
     * @param code 状态码
     * @param data 数据
     * @param msg 消息
     * @return 成功结果
     */
    public static Result success(Integer code, Object data, String msg) {
        Result result = new Result();
        result.setCode(code);
        result.setMsg(msg);
        result.setData(data);
        result.setTraceId(MDC.get(TRACE_ID_MDC_KEY));
        return result;
    }

    /**
     * 返回带分页总数的成功结果。
     * @param data 数据
     * @param count 总数
     * @return 成功结果
     */
    public static Result success(Object data, int count) {
        Result result = new Result();
        result.setCode(0);
        result.setMsg("request ok!");
        result.setData(data);
        result.setCount(count);
        result.setTraceId(MDC.get(TRACE_ID_MDC_KEY));
        return result;
    }

    /**
     * 返回错误结果。
     * @param code 错误码
     * @param msg 错误消息
     * @return 错误结果
     */
    public static Result error(int code, String msg) {
        Result result = new Result();
        result.setCode(code);
        result.setMsg(msg);
        result.setMessage(msg);
        result.setTraceId(MDC.get(TRACE_ID_MDC_KEY));
        return result;
    }

    /**
     * 返回默认错误结果
     * @return 错误结果
     */
    public static Result error() {
        return new Result();
    }

    /**
     * 返回带数据体的错误结果。
     * @param data 数据
     * @param code 错误码
     * @param msg 错误消息
     * @return 错误结果
     */
    public static Result error(Object data, int code, String msg) {
        Result result = new Result();
        result.setData(data);
        result.setCode(code);
        result.setMsg(msg);
        result.setMessage(msg);
        result.setTraceId(MDC.get(TRACE_ID_MDC_KEY));
        return result;
    }

    /**
     * 兼容历史 Layui 列表结构。
     * @param count 总数
     * @param data 数据
     * @return Map结果
     */
    public static Map<String, Object> lay_success(int count, Object data) {
        Map<String, Object> map = new HashMap<>();
        map.put("count", count);
        map.put("data", data);
        map.put("code", 0);
        map.put("msg", "request ok!");
        return map;
    }
}

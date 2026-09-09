package com.fly.rent.entity;

import lombok.Data;
import org.springframework.stereotype.Component;

/**
 * 项目统一返回结构。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
@Component
public class Result {

    /**
     * 业务状态码，0 表示成功。
     */
    private int code;

    /**
     * 响应消息。
     */
    private String msg;

    /**
     * 兼容新客户端的详细消息。成功时固定为 null，失败时与 msg 保持一致。
     */
    private String message;

    /**
     * 响应数据。
     */
    private Object data;

    /**
     * 列表总数。
     * 保留这个字段，是为了兼容项目里仍在使用的分页返回习惯。
     */
    private int count;

    /**
     * 请求追踪标识，由 TraceIdFilter 在请求入口生成或透传。
     */
    private String traceId;
}

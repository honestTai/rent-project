package com.fly.rent.web.support;

import com.fly.rent.entity.Order;
import lombok.Getter;

/**
 * 操作台账 ThreadLocal 上下文。
 * 业务方法在执行前调用 {@link #begin} 注册订单、操作类型、请求，
 * AOP 切面在方法结束后读取上下文并发布事件。
 */
public final class OperLogContext {

    private static final ThreadLocal<OperLogContext> HOLDER = new ThreadLocal<>();

    @Getter private final Order order;
    @Getter private final String beforeStatus;
    @Getter private final WebOrderOperation operation;
    @Getter private final WebRequest request;
    /** 操作人：ADMIN / MINIAPP / CALLBACK 等，为空时切面默认用 ADMIN */
    @Getter private final String operator;
    @Getter private Object resultBody;
    /** 自定义台账描述：同一 operType 下区分具体动作（如扣减押金-申请售后/确认扣款/撤销售后），为空时用枚举默认描述。 */
    @Getter private String description;

    private OperLogContext(Order order, WebOrderOperation operation, WebRequest request, String operator) {
        this.order = order;
        this.beforeStatus = order.getAlipayStatus();
        this.operation = operation;
        this.request = request;
        this.operator = operator;
    }

    /**
     * 注册台账上下文，自动快照 beforeStatus。操作人默认为 null，切面按 ADMIN 处理。
     */
    public static void begin(Order order, WebOrderOperation op, WebRequest req) {
        HOLDER.set(new OperLogContext(order, op, req, req == null ? null : req.operator()));
    }

    /**
     * 注册台账上下文并指定操作人（如 MINIAPP、CALLBACK），切面发布事件时使用。
     */
    public static void begin(Order order, WebOrderOperation op, WebRequest req, String operator) {
        HOLDER.set(new OperLogContext(order, op, req, operator));
    }

    /**
     * 设置自定义 resultBody（如同步操作返回的状态值）。
     */
    public static void setResultBody(Object body) {
        OperLogContext ctx = HOLDER.get();
        if (ctx != null) {
            ctx.resultBody = body;
        }
    }

    /**
     * 覆盖本次台账的中文描述，用于在同一 operType 下细分具体动作。
     */
    public static void setDescription(String description) {
        OperLogContext ctx = HOLDER.get();
        if (ctx != null && description != null && !description.trim().isEmpty()) {
            ctx.description = description.trim();
        }
    }

    /**
     * 获取台账描述：优先用自定义描述，没有时回退操作枚举的默认描述。
     */
    public String resolveDescription() {
        if (description != null && !description.trim().isEmpty()) {
            return description;
        }
        return operation == null ? null : operation.getDescription();
    }

    public static OperLogContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}

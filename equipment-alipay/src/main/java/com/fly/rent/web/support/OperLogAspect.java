package com.fly.rent.web.support;

import com.fly.rent.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;

/**
 * 操作台账 AOP 切面。
 * 拦截带有 {@link OperLog} 注解的方法，根据 {@link OperLogContext} 自动发布台账事件。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final ApplicationEventPublisher eventPublisher;

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint pjp, OperLog operLog) throws Throwable {
        try {
            Object result = pjp.proceed();
            publishSuccess(result);
            return result;
        } catch (Throwable t) {
            publishFailure(t);
            throw t;
        } finally {
            OperLogContext.clear();
        }
    }

    private void publishSuccess(Object result) {
        OperLogContext ctx = OperLogContext.current();
        if (ctx == null) return;

        try {
            Order order = ctx.getOrder();
            WebOrderOperation op = ctx.getOperation();
            String afterStatus = order.getAlipayStatus();

            String operator = ctx.getOperator() != null ? ctx.getOperator() : "ADMIN";
            Object requestBodySnapshot = ctx.getRequest().raw() != null ? new HashMap<>(ctx.getRequest().raw()) : null;
            eventPublisher.publishEvent(new OrderOperLogEvent(
                    order.getOrderId(), order.getOrderNo(),
                    op.getOperType(), ctx.resolveDescription(),
                    ctx.getBeforeStatus(), afterStatus,
                    requestBodySnapshot, ctx.getResultBody(),
                    true, null, operator));
        } catch (Exception e) {
            log.warn("发布台账成功事件失败", e);
        }
    }

    private void publishFailure(Throwable t) {
        OperLogContext ctx = OperLogContext.current();
        if (ctx == null) return;

        try {
            Order order = ctx.getOrder();
            WebOrderOperation op = ctx.getOperation();
            String failReason = buildFailReasonWithStack(t);

            String operator = ctx.getOperator() != null ? ctx.getOperator() : "ADMIN";
            Object requestBodySnapshot = ctx.getRequest().raw() != null ? new HashMap<>(ctx.getRequest().raw()) : null;
            eventPublisher.publishEvent(new OrderOperLogEvent(
                    order.getOrderId(), order.getOrderNo(),
                    op.getOperType(), ctx.resolveDescription(),
                    ctx.getBeforeStatus(), ctx.getBeforeStatus(),
                    requestBodySnapshot, null,
                    false, failReason, operator));
        } catch (Exception e) {
            log.warn("发布台账失败事件失败", e);
        }
    }

    /** 拼接异常信息与完整堆栈，便于台账中直接看到报错位置 */
    private static String buildFailReasonWithStack(Throwable t) {
        String msg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
        String stack = ExceptionUtils.getStackTrace(t);
        return msg + "\n堆栈:\n" + stack;
    }
}

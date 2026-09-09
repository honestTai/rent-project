package com.fly.rent.web.support;

import com.fly.rent.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 订单操作日志记录工具。
 * 作为非 AOP 场景（如 refundRenewal 等特殊方法）的便捷入口，
 * 内部通过发布 {@link OrderOperLogEvent} 实现异步写入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebOperLogHelper {

    public static final String OPERATOR_ADMIN = "ADMIN";
    public static final String OPERATOR_SCHEDULED_TASK = "SCHEDULED_TASK";
    public static final String OPERATOR_SYSTEM = "SYSTEM";
    /** 小程序用户侧操作 */
    public static final String OPERATOR_MINIAPP = "MINIAPP";
    /** 支付宝回调 */
    public static final String OPERATOR_CALLBACK = "CALLBACK";

    private final ApplicationEventPublisher eventPublisher;

    public void logSuccess(Order order, String operType, String operDesc,
                           String beforeStatus, String afterStatus,
                           Object requestBody, Object resultBody) {
        publish(order, operType, operDesc, beforeStatus, afterStatus, requestBody, resultBody, true, null, OPERATOR_ADMIN);
    }

    public void logSuccess(Order order, String operType, String operDesc,
                           String beforeStatus, String afterStatus,
                           Object requestBody, Object resultBody, String operator) {
        publish(order, operType, operDesc, beforeStatus, afterStatus, requestBody, resultBody, true, null, operator);
    }

    public void logFailure(Order order, String operType, String operDesc,
                           String beforeStatus, Object requestBody, String failReason) {
        publish(order, operType, operDesc, beforeStatus, beforeStatus, requestBody, null, false, failReason, OPERATOR_ADMIN);
    }

    public void logFailure(Order order, String operType, String operDesc,
                           String beforeStatus, Object requestBody, String failReason, String operator) {
        publish(order, operType, operDesc, beforeStatus, beforeStatus, requestBody, null, false, failReason, operator);
    }

    private void publish(Order order, String operType, String operDesc,
                         String beforeStatus, String afterStatus,
                         Object requestBody, Object resultBody,
                         boolean success, String failReason, String operator) {
        try {
            eventPublisher.publishEvent(new OrderOperLogEvent(
                    order.getOrderId(), order.getOrderNo(),
                    operType, operDesc,
                    beforeStatus, afterStatus,
                    requestBody, resultBody,
                    success, failReason, operator));
        } catch (Exception e) {
            log.error("发布操作日志事件失败: orderId={}, operType={}", order.getOrderId(), operType, e);
        }
    }
}

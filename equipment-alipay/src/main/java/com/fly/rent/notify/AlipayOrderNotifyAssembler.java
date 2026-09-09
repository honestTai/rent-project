package com.fly.rent.notify;

import com.fly.rent.entity.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 支付宝租赁订单通知字段组装器。
 * 统一从订单实体提取卡片展示所需的文案、金额摘要和链接参数，避免通知服务直接拼装细节字段。
 */
@Component
public class AlipayOrderNotifyAssembler {

    public String buildUserLabel(Order order) {
        if (order == null) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        if (hasText(order.getUserTitle())) {
            builder.append(order.getUserTitle());
        }
        if (hasText(order.getUserTel())) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(order.getUserTel());
        } else if (hasText(order.getUserUuid())) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(order.getUserUuid());
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }

    public String buildAmountSummary(Order order) {
        if (order == null) {
            return "-";
        }
        return "租金 " + formatCent(order.getOrderTotal()) + " / 押金 " + formatCent(order.getOrderDeposit());
    }

    public String resolveEventName(String status) {
        if (!hasText(status)) {
            return "订单状态更新";
        }
        String normalized = status.trim().toUpperCase();
        if ("CREATED".equals(normalized)) {
            return "创建订单";
        }
        if ("SIGNED".equals(normalized)) {
            return "用户签约";
        }
        if ("PAID".equals(normalized)) {
            return "支付成功";
        }
        if ("APPROVED".equals(normalized)) {
            return "审核通过";
        }
        if ("DELIVERED".equals(normalized)) {
            return "商家发货";
        }
        if ("RECEIVED".equals(normalized)) {
            return "用户确认收货";
        }
        if ("RETURN_DELIVERED".equals(normalized)) {
            return "用户寄回";
        }
        if ("RETURN_RECEIVED".equals(normalized)) {
            return "商家确认收回";
        }
        if ("FINISHED".equals(normalized)) {
            return "订单完成";
        }
        if ("PENDING_CANCEL".equals(normalized)) {
            return "退款申请";
        }
        if ("CLOSED".equals(normalized)) {
            return "订单关闭";
        }
        return "状态更新:" + normalized;
    }

    public String resolveStatusLabel(String status) {
        return resolveEventName(status);
    }

    private String formatCent(Integer cents) {
        if (cents == null) {
            return "0.00";
        }
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

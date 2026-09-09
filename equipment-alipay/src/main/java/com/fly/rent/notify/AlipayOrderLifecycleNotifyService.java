package com.fly.rent.notify;

import com.common.notify.feishu.FeishuNotifyProperties;
import com.common.notify.feishu.FeishuNotifyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.miniapp.order.RentContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 支付宝租赁订单生命周期通知服务。
 * 负责把分散在建单、回调、审核、发货、退款等链路里的状态变化统一转换为飞书卡片，并做 Redis 去重。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayOrderLifecycleNotifyService {

    private static final int DEDUP_HOURS = 24;

    private final FeishuNotifyService feishuNotifyService;
    private final FeishuNotifyProperties feishuNotifyProperties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AlipayOrderNotifyAssembler notifyAssembler;
    private final ObjectMapper objectMapper;
    private final RentContractService rentContractService;

    /**
     * 建单成功后发送创建通知。
     * 这一步还没有依赖回调，因此单独走 CREATED 事件通知。
     */
    public void notifyOrderCreated(Order order) {
        if (order == null || order.getOrderId() == null) {
            return;
        }
        String dedupKey = buildDedupKey(order.getOrderId(), "CREATED");
        if (!acquireDedup(dedupKey)) {
            return;
        }
        sendLifecycleCard(order, null, "CREATED", "小程序建单");
    }

    /**
     * 当订单状态真的发生变化时发送生命周期通知。
     * 由业务入口先传入 old/new 状态，统一在这里完成事件名映射和去重。
     */
    public void notifyStatusChanged(Order order, String oldStatus, String newStatus, String source) {
        if (order == null || order.getOrderId() == null || !hasText(newStatus)) {
            return;
        }
        String normalizedOld = normalizeStatus(oldStatus);
        String normalizedNew = normalizeStatus(newStatus);
        if (normalizedNew.equals(normalizedOld)) {
            return;
        }
        if (AlipayRentConstants.STATUS_SIGNED.equals(normalizedNew)) {
            rentContractService.generateSignedContractAsync(order.getOrderId(), source);
        }
        String dedupKey = buildDedupKey(order.getOrderId(), normalizedNew);
        if (!acquireDedup(dedupKey)) {
            return;
        }
        sendLifecycleCard(order, normalizedOld, normalizedNew, source);
    }

    /**
     * 发送异常类通知。
     * 这类通知不依赖状态变化，主要用于回调缺参、订单找不到、退款/回调处理失败等需要人工关注的场景。
     */
    public void notifyException(String scene, String summary, String detail, Order order, Map<String, Object> extraContext) {
        String actionUrl = order == null ? feishuNotifyProperties.getAlipayLinks().getOrderList() : buildDetailUrl(order);
        String renderedDetail = detail;
        if (extraContext != null && !extraContext.isEmpty()) {
            try {
                renderedDetail = detail + " | 上下文: " + objectMapper.writeValueAsString(extraContext);
            } catch (Exception ignore) {
                renderedDetail = detail + " | 上下文序列化失败";
            }
        }
        String uuidSuffix = order == null
                ? scene + "-" + System.currentTimeMillis()
                : scene + "-" + order.getOrderId() + "-" + System.currentTimeMillis();
        feishuNotifyService.sendExceptionCard(
                "alipay",
                "[支付宝租赁] 异常提醒",
                scene,
                summary,
                renderedDetail,
                "查看订单列表",
                actionUrl,
                uuidSuffix
        );
    }

    private void sendLifecycleCard(Order order, String oldStatus, String newStatus, String source) {
        String eventName = notifyAssembler.resolveEventName(newStatus);
        String detailUrl = buildDetailUrl(order);
        String listUrl = feishuNotifyProperties.getAlipayLinks().getOrderList();
        feishuNotifyService.sendAlipayOrderLifecycleCard(
                eventName,
                order.getOrderNo(),
                order.getGoodTitle(),
                notifyAssembler.buildUserLabel(order),
                notifyAssembler.resolveStatusLabel(newStatus),
                notifyAssembler.buildAmountSummary(order),
                order.getCourno(),
                order.getUpdateTime() == null ? new Date() : order.getUpdateTime(),
                buildSource(source, oldStatus, newStatus),
                detailUrl,
                listUrl,
                order.getOrderId() + "-" + normalizeStatus(newStatus)
        );
    }

    private String buildSource(String source, String oldStatus, String newStatus) {
        StringBuilder builder = new StringBuilder();
        builder.append(hasText(source) ? source : "系统同步");
        if (hasText(oldStatus)) {
            builder.append(" | ").append(normalizeStatus(oldStatus)).append(" -> ").append(normalizeStatus(newStatus));
        }
        return builder.toString();
    }

    private String buildDetailUrl(Order order) {
        String template = feishuNotifyProperties.getAlipayLinks().getOrderDetailTemplate();
        if (!hasText(template)) {
            return feishuNotifyProperties.getAlipayLinks().getOrderList();
        }
        return template.replace("{orderId}", String.valueOf(order.getOrderId()));
    }

    private boolean acquireDedup(String key) {
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", DEDUP_HOURS, TimeUnit.HOURS);
            return Boolean.TRUE.equals(success);
        } catch (Exception e) {
            log.warn("飞书通知去重写入失败，按允许发送处理: {}", e.getMessage());
            return true;
        }
    }

    private String buildDedupKey(Integer orderId, String status) {
        return "feishu:alipay:order:" + orderId + ":" + normalizeStatus(status);
    }

    private String normalizeStatus(String status) {
        return hasText(status) ? status.trim().toUpperCase() : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

package com.fly.rent.web.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.entity.OrderOperLog;
import com.fly.rent.mapper.OrderOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步消费 {@link OrderOperLogEvent}，将操作台账写入 DB。
 * 写入失败仅记录错误日志，不影响业务流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOperLogEventListener {

    private final OrderOperLogMapper logMapper;
    private final ObjectMapper objectMapper;

    @Async("operLogExecutor")
    @EventListener
    public void onOperLog(OrderOperLogEvent event) {
        try {
            OrderOperLog operLog = new OrderOperLog();
            operLog.setOrderId(event.getOrderId());
            operLog.setOrderNo(event.getOrderNo());
            operLog.setOperType(event.getOperType());
            operLog.setOperDesc(event.getOperDesc());
            operLog.setBeforeStatus(event.getBeforeStatus());
            operLog.setAfterStatus(event.getAfterStatus());
            operLog.setRequestBody(toJson(event.getRequestBody()));
            operLog.setResultBody(toJson(event.getResultBody()));
            operLog.setSuccess(event.isSuccess() ? 1 : 0);
            operLog.setFailReason(event.getFailReason());
            operLog.setOperator(event.getOperator());
            operLog.setCreatetime(System.currentTimeMillis());
            logMapper.insert(operLog);
        } catch (Exception e) {
            log.error("异步写入操作日志失败: orderId={}, operType={}", event.getOrderId(), event.getOperType(), e);
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}

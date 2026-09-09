package com.common.log;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 日志事件发布器
 * 各切面通过此类发布日志事件，由对应的监听器异步消费
 */
@Component
public class LogEventPublisher {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 发布日志事件
     */
    public void publish(LogEvent event) {
        eventPublisher.publishEvent(event);
    }
}

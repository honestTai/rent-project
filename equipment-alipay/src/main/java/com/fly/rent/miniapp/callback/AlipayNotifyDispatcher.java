package com.fly.rent.miniapp.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 支付宝通知分发器。
 *
 * 路由规则：
 * 1. 统一验签；
 * 2. 遍历所有通知处理器，找到第一个支持当前报文的处理器；
 * 3. 未命中处理器时按成功响应，避免支付宝因为未知消息不断重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayNotifyDispatcher {

    private final AlipayNotifyVerifier notifyVerifier;
    private final List<AlipayNotifyHandler> handlers;

    public String dispatch(Map<String, String> params) {
        if (!notifyVerifier.verify(params)) {
            return "failure";
        }
        for (AlipayNotifyHandler handler : handlers) {
            if (handler.supports(params)) {
                return handler.handle(params);
            }
        }
        log.warn("未匹配到支付宝通知处理器: params={}", params);
        return "success";
    }
}

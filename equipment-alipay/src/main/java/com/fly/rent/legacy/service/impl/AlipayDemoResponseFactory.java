package com.fly.rent.legacy.service.impl;

import com.alipay.api.AlipayRequest;
import com.alipay.api.AlipayResponse;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

/** 为显式开启的 Demo 环境创建可识别、不可冒充真实流水的支付宝模拟响应。 */
@Slf4j
final class AlipayDemoResponseFactory {

    static final String DEMO_MESSAGE = "Demo 环境已阻断支付宝 API，本次操作为本地模拟";

    private AlipayDemoResponseFactory() {
    }

    static <T extends AlipayResponse> T success(AlipayRequest<T> request) {
        try {
            T response = request.getResponseClass().getDeclaredConstructor().newInstance();
            response.setCode("10000");
            response.setMsg("Success");
            response.setSubMsg(DEMO_MESSAGE);
            response.setBody("{\"demoMode\":true,\"realAlipayRequest\":false}");
            response.setParams(Collections.singletonMap("demoMode", "true"));
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            setIfPresent(response, "setTradeNo", "DEMO-TRADE-" + suffix);
            setIfPresent(response, "setOperationNo", "DEMO-OP-" + suffix);
            setIfPresent(response, "setAftersaleNo", "DEMO-AFTERSALE-" + suffix);
            setIfPresent(response, "setOutAftersaleId", "DEMO-OUT-AFTERSALE-" + suffix);
            setIfPresent(response, "setConfirmRequestNo", "DEMO-CONFIRM-" + suffix);
            setIfPresent(response, "setFileId", "DEMO-FILE-" + suffix);
            setIfPresent(response, "setAgreementNo", "DEMO-AGREEMENT-" + suffix);
            log.info("Demo模式已阻断支付宝API: method={}, response={}",
                    request.getApiMethodName(), request.getResponseClass().getSimpleName());
            return response;
        } catch (Exception e) {
            throw new IllegalStateException("无法创建支付宝Demo模拟响应: " + request.getApiMethodName(), e);
        }
    }

    private static void setIfPresent(AlipayResponse response, String setter, String value) {
        try {
            Method method = response.getClass().getMethod(setter, String.class);
            method.invoke(response, value);
        } catch (NoSuchMethodException ignore) {
            // 不同支付宝响应类型字段不同，没有该字段属于正常情况。
        } catch (Exception e) {
            throw new IllegalStateException("设置支付宝Demo响应字段失败: " + setter, e);
        }
    }
}

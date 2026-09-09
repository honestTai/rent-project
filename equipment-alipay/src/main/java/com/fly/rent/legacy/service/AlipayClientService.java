package com.fly.rent.legacy.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayRequest;
import com.alipay.api.AlipayResponse;

/**
 * 支付宝客户端抽象。
 * 将 SDK 调用统一封装为可替换依赖，便于后续接入监控、限流或 mock。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public interface AlipayClientService {

    /**
     * 执行一笔支付宝开放平台请求。
     *
     * @param request 支付宝请求对象
     * @param <T> 响应类型
     * @return 支付宝响应对象
     * @throws AlipayApiException 支付宝 SDK 异常
     */
    <T extends AlipayResponse> T execute(AlipayRequest<T> request) throws AlipayApiException;

    /**
     * 执行需要用户授权 token 的支付宝开放平台请求。
     *
     * @param request 支付宝请求对象
     * @param authToken 用户授权 token
     * @param <T> 响应类型
     * @return 支付宝响应对象
     * @throws AlipayApiException 支付宝 SDK 异常
     */
    <T extends AlipayResponse> T execute(AlipayRequest<T> request, String authToken) throws AlipayApiException;

    /**
     * 执行移动端 SDK 类请求，返回客户端可直接唤起的签名参数串。
     *
     * @param request 支付宝请求对象
     * @param <T> 响应类型
     * @return 签名后的请求参数串
     * @throws AlipayApiException 支付宝 SDK 异常
     */
    <T extends AlipayResponse> String sdkExecute(AlipayRequest<T> request) throws AlipayApiException;

    /**
     * 页面类请求，返回跳转 URL（如实人认证 certify）。
     *
     * @param request 支付宝请求对象
     * @param httpMethod GET 或 POST
     * @return 完整请求 URL
     * @throws AlipayApiException 支付宝 SDK 异常
     */
    String pageExecute(AlipayRequest request, String httpMethod) throws AlipayApiException;
}

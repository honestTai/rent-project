package com.fly.rent.legacy.service.impl;

import com.fly.rent.legacy.dto.apilyRentUtil.BaseApily;
import com.fly.rent.legacy.service.AlipayClientService;
import com.fly.rent.config.AlipayPlatformConfigService;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayRequest;
import com.alipay.api.AlipayResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 支付宝客户端实现。
 * 当前直接代理 `BaseApily` 中的 AlipayClient，后续扩展时只需要在这里增强。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Service
public class AlipayClientServiceImpl implements AlipayClientService {

    @Autowired
    private BaseApily baseApily;

    @Autowired
    private AlipayPlatformConfigService configService;

    /**
     * 执行支付宝 API 请求（同步调用），用于创建租赁单、查询、关单、退款、履约审核等。
     *
     * @param request 支付宝请求对象，如 AlipayCommerceRentOrderCreateRequest、AlipayCommerceRentOrderQueryRequest 等。
     * @param <T>     响应类型，与 request 泛型一致，如 AlipayCommerceRentOrderCreateResponse。
     * @return 支付宝响应对象，调用方需自行判断 response.isSuccess() 及取业务字段。
     * @throws AlipayApiException 网络、签名、参数等导致的支付宝侧异常。
     */
    @Override
    public <T extends AlipayResponse> T execute(AlipayRequest<T> request) throws AlipayApiException {
        if (configService.demoModeEnabled()) {
            return AlipayDemoResponseFactory.success(request);
        }
        return baseApily.getAlipayClient().execute(request);
    }

    @Override
    public <T extends AlipayResponse> T execute(AlipayRequest<T> request, String authToken) throws AlipayApiException {
        if (configService.demoModeEnabled()) {
            return AlipayDemoResponseFactory.success(request);
        }
        return baseApily.getAlipayClient().execute(request, authToken);
    }

    @Override
    public <T extends AlipayResponse> String sdkExecute(AlipayRequest<T> request) throws AlipayApiException {
        if (configService.demoModeEnabled()) {
            AlipayDemoResponseFactory.success(request);
            return "demo_mode=true&real_alipay_request=false";
        }
        AlipayResponse response = baseApily.getAlipayClient().sdkExecute(request);
        return response != null ? response.getBody() : null;
    }

    /**
     * 执行支付宝「页面类」请求，返回表单或跳转 URL 的 body（如签约、支付等需在客户端跳转的场景）。
     *
     * @param request   支付宝请求对象（如签约、支付授权等）。
     * @param httpMethod 请求方法，如 "GET" 或 "POST"，决定返回 form 还是 URL。
     * @return 响应 body 字符串（表单 HTML 或 URL），无 body 时返回 null。
     * @throws AlipayApiException 支付宝接口异常。
     */
    @Override
    public String pageExecute(AlipayRequest request, String httpMethod) throws AlipayApiException {
        if (configService.demoModeEnabled()) {
            AlipayDemoResponseFactory.success(request);
            return "DEMO_MODE_BLOCKED";
        }
        AlipayResponse response = baseApily.getAlipayClient().pageExecute(request, httpMethod);
        return response != null ? response.getBody() : null;
    }
}

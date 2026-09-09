package com.fly.rent.miniapp.user;

import com.alipay.api.AlipayApiException;
import com.alipay.api.request.AlipayUserCertifyOpenCertifyRequest;
import com.alipay.api.request.AlipayUserCertifyOpenInitializeRequest;
import com.alipay.api.response.AlipayUserCertifyOpenInitializeResponse;
import com.common.zhongtai.config.ZhongtaiConfigService;
import com.fly.rent.common.support.RentApiException;
import com.fly.rent.legacy.service.AlipayClientService;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付宝实人认证初始化。
 * 调用 initialize 获取 certify_id，再调用 certify 获取前端 my.startAPVerify 所需的 url。
 */
@RequiredArgsConstructor
@Service
public class AlipayCertifyInitService {

    private final AlipayClientService alipayClientService;
    private final ZhongtaiConfigService zhongtaiConfigService;

    /**
     * 初始化实人认证，返回小程序 my.startAPVerify 所需的 certifyId 和 url。
     *
     * @param name   真实姓名
     * @param idCard 身份证号
     * @return Map 含 certifyId、url
     */
    public Map<String, String> initCertify(String name, String idCard) {
        if (!StringUtils.hasText(name) || !StringUtils.hasText(idCard) || idCard.trim().length() < 15) {
            throw new RentApiException(400, "姓名和身份证号格式不正确");
        }
        String trimName = name.trim();
        String trimIdCard = idCard.trim();

        try {
            // 1. initialize 获取 certify_id
            AlipayUserCertifyOpenInitializeRequest initRequest = new AlipayUserCertifyOpenInitializeRequest();
            JSONObject bizContent = new JSONObject();
            bizContent.put("outer_order_no", "CERT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 32));
            bizContent.put("biz_code", "FACE");
            JSONObject identityParam = new JSONObject();
            identityParam.put("identity_type", "CERT_INFO");
            identityParam.put("cert_type", "IDENTITY_CARD");
            identityParam.put("cert_name", trimName);
            identityParam.put("cert_no", trimIdCard);
            bizContent.put("identity_param", identityParam);
            JSONObject merchantConfig = new JSONObject();
            merchantConfig.put("return_url", zhongtaiConfigService.getString("alipay", "alipay.certify.return-url"));
            bizContent.put("merchant_config", merchantConfig);
            initRequest.setBizContent(bizContent.toJSONString());

            AlipayUserCertifyOpenInitializeResponse initResponse = alipayClientService.execute(initRequest);
            if (initResponse == null || !initResponse.isSuccess()) {
                throw new RentApiException(500, "认证初始化失败：" + (initResponse != null ? initResponse.getSubMsg() : "未知"));
            }
            String certifyId = initResponse.getCertifyId();
            if (!StringUtils.hasText(certifyId)) {
                throw new RentApiException(500, "认证初始化未返回 certifyId");
            }

            // 2. certify 获取前端跳转 url
            AlipayUserCertifyOpenCertifyRequest certifyRequest = new AlipayUserCertifyOpenCertifyRequest();
            certifyRequest.setBizContent("{\"certify_id\":\"" + certifyId.replace("\"", "\\\"") + "\"}");
            String url = alipayClientService.pageExecute(certifyRequest, "GET");
            if (!StringUtils.hasText(url)) {
                throw new RentApiException(500, "获取认证地址失败");
            }

            Map<String, String> result = new HashMap<>(2);
            result.put("certifyId", certifyId);
            result.put("url", url);
            return result;
        } catch (AlipayApiException e) {
            throw new RentApiException(500, "支付宝认证初始化异常：" + e.getMessage());
        }
    }
}

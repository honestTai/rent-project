package com.fly.rent.miniapp.user;

import com.alipay.api.AlipayApiException;
import com.alipay.api.request.AlipayUserCertifyOpenQueryRequest;
import com.alipay.api.response.AlipayUserCertifyOpenQueryResponse;
import com.fly.rent.legacy.service.AlipayClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付宝实人认证结果查询。
 * 调用 alipay.user.certify.open.query，小程序端完成 my.startAPVerify 后携带 certifyId 查询是否通过。
 */
@RequiredArgsConstructor
@Service
public class AlipayCertifyQueryService {

    private final AlipayClientService alipayClientService;

    /**
     * 查询认证是否通过。
     *
     * @param certifyId 认证流程号（来自 initialize 或小程序认证完成后回调）
     * @return true 表示通过，false 表示未通过或异常
     */
    public boolean isPassed(String certifyId) {
        if (!StringUtils.hasText(certifyId)) {
            return false;
        }
        try {
            AlipayUserCertifyOpenQueryRequest request = new AlipayUserCertifyOpenQueryRequest();
            request.setBizContent("{\"certify_id\":\"" + certifyId.replace("\"", "\\\"") + "\"}");
            AlipayUserCertifyOpenQueryResponse response = alipayClientService.execute(request);
            if (response == null || !response.isSuccess()) {
                return false;
            }
            return "T".equals(response.getPassed());
        } catch (AlipayApiException e) {
            return false;
        }
    }
}

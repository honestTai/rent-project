package com.fly.rent.miniapp.callback;

import com.alipay.api.internal.util.AlipaySignature;
import com.common.zhongtai.config.ZhongtaiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 支付宝异步通知验签器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayNotifyVerifier {

    private static final String SYSTEM_CODE = "alipay";

    private final ZhongtaiConfigService zhongtaiConfigService;

    public boolean verify(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            log.warn("支付宝通知验签失败: 通知参数为空");
            return false;
        }
        try {
            String publicKey = config("alipay.public-key");
            String charset = firstNotBlank(params.get("charset"), config("alipay.notify.charset"));
            String signType = firstNotBlank(params.get("sign_type"), config("alipay.notify.sign-type"));
            boolean verified = AlipaySignature.rsaCheckV2(params, publicKey, charset, signType);
            if (!verified) {
                log.warn("支付宝通知验签失败: notify_id={}, msg_method={}, notify_type={}",
                        params.get("notify_id"), params.get("msg_method"), params.get("notify_type"));
            }
            return verified;
        } catch (Exception e) {
            log.error("支付宝通知验签异常: notify_id={}, msg_method={}, notify_type={}",
                    params.get("notify_id"), params.get("msg_method"), params.get("notify_type"), e);
            return false;
        }
    }

    private String config(String key) {
        return zhongtaiConfigService.getString(SYSTEM_CODE, key);
    }

    private String firstNotBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}

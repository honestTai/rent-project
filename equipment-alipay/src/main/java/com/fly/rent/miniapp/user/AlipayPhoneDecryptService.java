package com.fly.rent.miniapp.user;

import com.alipay.api.internal.util.AlipayEncrypt;
import com.alipay.api.internal.util.AlipaySignature;
import com.common.zhongtai.config.ZhongtaiConfigService;
import com.fly.rent.common.support.RentApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 支付宝手机号解密服务。
 */
@Service
public class AlipayPhoneDecryptService {

    private static final String CHARSET = "UTF-8";
    private static final String SIGN_TYPE = "RSA2";
    private static final String ENCRYPT_TYPE = "AES";

    @Autowired
    private ZhongtaiConfigService zhongtaiConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 从 my.getPhoneNumber() 回传报文中验签并解密出手机号。
     *
     * @param rawRequest 前端透传的授权报文（兼容字符串或对象，内含 response 与 sign）
     * @return 手机号
     */
    public String decryptAndGetMobile(JsonNode rawRequest) {
        try {
            JsonNode root = normalizeEnvelope(rawRequest);
            String encryptedContent = firstNotBlank(
                    text(root, "response"),
                    text(root, "encryptedData"),
                    text(root, "encryptedContent"),
                    text(root, "encrypted_data")
            );
            String sign = text(root, "sign");
            if (!StringUtils.hasText(encryptedContent) || !StringUtils.hasText(sign)) {
                throw new RentApiException(400, "支付宝手机号授权数据缺少 response/sign");
            }

            String charset = firstNotBlank(text(root, "charset"), CHARSET);
            String signType = firstNotBlank(text(root, "sign_type"), text(root, "signType"), SIGN_TYPE);
            String encryptType = firstNotBlank(text(root, "encrypt_type"), text(root, "encryptType"), ENCRYPT_TYPE);
            boolean encrypted = !encryptedContent.trim().startsWith("{");
            String publicKey = requiredConfig("alipay.public-key", "支付宝公钥 alipay.public-key");
            boolean signOk = verifySignature(encryptedContent, encrypted, sign, publicKey, charset, signType);
            if (!signOk) {
                throw new RentApiException(400, "支付宝手机号授权数据验签失败，请检查支付宝公钥配置");
            }

            String decrypted = encrypted
                    ? decryptContent(encryptedContent, encryptType, charset)
                    : encryptedContent;
            JsonNode phonePayload = objectMapper.readTree(decrypted);
            JsonNode phoneData = phonePayload.get("response") != null && phonePayload.get("response").isObject()
                    ? phonePayload.get("response")
                    : phonePayload;
            String mobile = firstNotBlank(
                    text(phoneData, "mobile"),
                    text(phoneData, "phoneNumber"),
                    text(phoneData, "mobilePhone"),
                    text(phoneData, "purePhoneNumber"),
                    text(phoneData, "phone")
            );
            if (!StringUtils.hasText(mobile)) {
                throw new RentApiException(400, "未解析到手机号: code=" + safeText(phonePayload, "code")
                        + ", msg=" + safeText(phonePayload, "msg")
                        + ", subCode=" + safeText(phonePayload, "subCode")
                        + ", subMsg=" + safeText(phonePayload, "subMsg"));
            }
            return mobile;
        } catch (RentApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RentApiException(500, "手机号解密处理失败，请检查授权报文格式");
        }
    }

    /**
     * 兼容旧单测或旧调用方传入 JSON 字符串。
     *
     * @param rawResponse 支付宝授权报文 JSON 字符串
     * @return 手机号
     */
    public String decryptAndGetMobile(String rawResponse) {
        if (!StringUtils.hasText(rawResponse)) {
            throw new RentApiException(400, "response 不能为空");
        }
        try {
            return decryptAndGetMobile(objectMapper.readTree(rawResponse));
        } catch (RentApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RentApiException(400, "response 不是合法 JSON");
        }
    }

    private JsonNode normalizeEnvelope(JsonNode rawRequest) throws Exception {
        if (rawRequest == null || rawRequest.isNull()) {
            throw new RentApiException(400, "response 不能为空");
        }
        if (rawRequest.isTextual()) {
            return parseEnvelopeText(rawRequest.asText());
        }
        if (!rawRequest.isObject()) {
            throw new RentApiException(400, "手机号授权数据格式不正确");
        }
        JsonNode responseNode = rawRequest.get("response");
        if (responseNode == null || responseNode.isNull()) {
            if (hasAnyText(rawRequest, "encryptedData", "encryptedContent", "encrypted_data")) {
                return rawRequest;
            }
            throw new RentApiException(400, "手机号授权数据缺少 response");
        }
        if (responseNode.isObject()) {
            return responseNode;
        }
        if (responseNode.isTextual()) {
            JsonNode parsed = tryParseObject(responseNode.asText());
            if (parsed != null && hasAnyText(parsed, "response", "encryptedData", "encryptedContent", "encrypted_data")) {
                return parsed;
            }
            return rawRequest;
        }
        throw new RentApiException(400, "response 格式不正确");
    }

    private JsonNode parseEnvelopeText(String value) throws Exception {
        if (!StringUtils.hasText(value)) {
            throw new RentApiException(400, "response 不能为空");
        }
        JsonNode parsed = tryParseObject(value);
        if (parsed == null) {
            throw new RentApiException(400, "response 不是合法 JSON");
        }
        return parsed;
    }

    private JsonNode tryParseObject(String value) throws Exception {
        if (!StringUtils.hasText(value) || !value.trim().startsWith("{")) {
            return null;
        }
        JsonNode parsed = objectMapper.readTree(value);
        return parsed != null && parsed.isObject() ? parsed : null;
    }

    private boolean verifySignature(
            String encryptedContent,
            boolean encrypted,
            String sign,
            String publicKey,
            String charset,
            String signType
    ) {
        List<String> candidates = new ArrayList<>();
        if (encrypted) {
            candidates.add("\"" + encryptedContent + "\"");
        }
        candidates.add(encryptedContent);
        for (String signContent : candidates) {
            try {
                if (AlipaySignature.rsaCheck(signContent, sign, publicKey, charset, signType)) {
                    return true;
                }
            } catch (Exception ignored) {
                // Try the next compatible sign content shape.
            }
        }
        return false;
    }

    private String decryptContent(String encryptedContent, String encryptType, String charset) {
        String secretKey = requiredConfig("alipay.secret-key", "支付宝手机号解密 AES 密钥 alipay.secret-key");
        try {
            return AlipayEncrypt.decryptContent(encryptedContent, encryptType, secretKey, charset);
        } catch (Exception ex) {
            throw new RentApiException(500, "支付宝手机号解密失败，请检查配置中心 alipay.secret-key 是否为当前小程序的接口内容加密 AES 密钥");
        }
    }

    private String text(JsonNode node, String key) {
        if (node == null || node.get(key) == null || node.get(key).isNull()) {
            return null;
        }
        String value = node.get(key).asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private String safeText(JsonNode node, String key) {
        String value = text(node, key);
        return value == null ? "" : value;
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasAnyText(JsonNode node, String... keys) {
        for (String key : keys) {
            if (StringUtils.hasText(text(node, key))) {
                return true;
            }
        }
        return false;
    }

    private String requiredConfig(String key, String displayName) {
        String value;
        try {
            value = config(key);
        } catch (Exception ex) {
            throw new RentApiException(500, "读取" + displayName + "失败");
        }
        if (!StringUtils.hasText(value)) {
            throw new RentApiException(500, displayName + " 未配置，请在配置中心补齐");
        }
        return value.trim();
    }

    /**
     * 读取支付宝中台配置。
     *
     * @param key 配置键
     * @return 配置值
     */
    private String config(String key) {
        return zhongtaiConfigService.getString("alipay", key);
    }
}

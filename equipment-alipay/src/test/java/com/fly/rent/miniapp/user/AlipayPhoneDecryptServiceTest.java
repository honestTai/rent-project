package com.fly.rent.miniapp.user;

import com.alipay.api.internal.util.AlipayEncrypt;
import com.alipay.api.internal.util.AlipaySignature;
import com.common.zhongtai.config.ZhongtaiConfigService;
import com.fly.rent.common.support.RentApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlipayPhoneDecryptServiceTest {

    private static final String AES_KEY = "yhsGSEGeIlsfbsh9j52tmA==";
    private static final String CHARSET = "UTF-8";
    private static final String SIGN_TYPE = "RSA2";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void decryptAndGetMobileVerifiesSignatureAndDecryptsAesResponse() throws Exception {
        Fixture fixture = signedEncryptedResponse();
        AlipayPhoneDecryptService service = serviceWithConfig(fixture.publicKey, AES_KEY);

        assertEquals("13800138000", service.decryptAndGetMobile(objectMapper.writeValueAsString(fixture.rawResponse)));
    }

    @Test
    void decryptAndGetMobileAcceptsNestedObjectResponse() throws Exception {
        Fixture fixture = signedEncryptedResponse();
        ObjectNode request = objectMapper.createObjectNode();
        request.set("response", objectMapper.valueToTree(fixture.rawResponse));
        AlipayPhoneDecryptService service = serviceWithConfig(fixture.publicKey, AES_KEY);

        assertEquals("13800138000", service.decryptAndGetMobile(request));
    }

    @Test
    void decryptAndGetMobileReportsMissingAesKey() throws Exception {
        Fixture fixture = signedEncryptedResponse();
        AlipayPhoneDecryptService service = serviceWithConfig(fixture.publicKey, "");

        RentApiException ex = assertThrows(RentApiException.class,
                () -> service.decryptAndGetMobile(objectMapper.valueToTree(fixture.rawResponse)));

        assertEquals(500, ex.getCode());
        assertEquals("支付宝手机号解密 AES 密钥 alipay.secret-key 未配置，请在配置中心补齐", ex.getMessage());
    }

    private Fixture signedEncryptedResponse() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        String privateKey = encode(keyPair.getPrivate());
        String publicKey = encode(keyPair.getPublic());
        String encryptedResponse = AlipayEncrypt.encryptContent(
                "{\"code\":\"10000\",\"msg\":\"Success\",\"mobile\":\"13800138000\"}",
                "AES",
                AES_KEY,
                CHARSET);
        String sign = AlipaySignature.rsaSign("\"" + encryptedResponse + "\"", privateKey, CHARSET, SIGN_TYPE);

        Map<String, String> rawResponse = new HashMap<>();
        rawResponse.put("response", encryptedResponse);
        rawResponse.put("sign", sign);
        rawResponse.put("sign_type", SIGN_TYPE);
        rawResponse.put("encrypt_type", "AES");
        rawResponse.put("charset", CHARSET);

        return new Fixture(rawResponse, publicKey);
    }

    private AlipayPhoneDecryptService serviceWithConfig(String publicKey, String aesKey) {
        AlipayPhoneDecryptService service = new AlipayPhoneDecryptService();
        Map<String, String> configValues = new HashMap<>();
        configValues.put("alipay.public-key", publicKey);
        configValues.put("alipay.secret-key", aesKey);
        ReflectionTestUtils.setField(service, "zhongtaiConfigService", new StubZhongtaiConfigService(configValues));
        return service;
    }

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String encode(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static class Fixture {
        private final Map<String, String> rawResponse;
        private final String publicKey;

        private Fixture(Map<String, String> rawResponse, String publicKey) {
            this.rawResponse = rawResponse;
            this.publicKey = publicKey;
        }
    }

    private static class StubZhongtaiConfigService extends ZhongtaiConfigService {
        private final Map<String, String> values;

        StubZhongtaiConfigService(Map<String, String> values) {
            super(null);
            this.values = values;
        }

        @Override
        public String getString(String systemCode, String configKey) {
            return values.get(configKey);
        }
    }
}

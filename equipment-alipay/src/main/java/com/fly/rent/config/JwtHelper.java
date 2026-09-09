package com.fly.rent.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.common.zhongtai.config.ZhongtaiConfigService;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;

/**
 * 小程序 JWT 签发与校验（仅用于 /api/rent/v1/miniapp/**）。
 * 使用服务端密钥 + 会话密钥参与签名，并包含 iss/aud/iat/exp 以提升安全性。
 *
 * @author HonestTat
 * @since 2026-03-12
 */
@Component
public class JwtHelper {

    private static final String CLAIM_USER_NAME = "userName";
    /** 服务端密钥与会话密钥拼接分隔符，避免简单拼接导致边界问题 */
    private static final String KEY_DELIMITER = "|";
    private static final String SYSTEM_CODE = "alipay";
    private static final String KEY_SERVER_SECRET = "miniapp.jwt.server-secret";
    private static final String KEY_ISSUER = "miniapp.jwt.issuer";
    private static final String KEY_AUDIENCE = "miniapp.jwt.audience";
    private static final String KEY_EXPIRE_MINUTES = "miniapp.jwt.expire-minutes";

    private final ZhongtaiConfigService zhongtaiConfigService;

    public JwtHelper(ZhongtaiConfigService zhongtaiConfigService) {
        this.zhongtaiConfigService = zhongtaiConfigService;
    }

    @PostConstruct
    public void init() {
        String secret = serverSecret();
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("中台配置缺失: systemCode=alipay, configKey=" + KEY_SERVER_SECRET);
        }
    }

    /**
     * 生成签名密钥：服务端密钥 + 会话密钥，提高伪造难度。
     */
    private String signingKey(String sessionSecret) {
        return serverSecret() + KEY_DELIMITER + sessionSecret;
    }

    /**
     * 签发 token。
     *
     * @param userName      会话用户名（如 rent:alipayUserId）
     * @param sessionSecret 会话密钥（与 Redis 中一致）
     * @return JWT 字符串，失败返回 null
     */
    public String sign(String userName, String sessionSecret) {
        if (userName == null || sessionSecret == null) {
            return null;
        }
        try {
            Algorithm algorithm = Algorithm.HMAC384(signingKey(sessionSecret));
            long now = System.currentTimeMillis();
            Date expiresAt = new Date(now + (long) getExpireMinutes() * 60 * 1000);
            return JWT.create()
                    .withClaim(CLAIM_USER_NAME, userName)
                    .withIssuer(issuer())
                    .withAudience(audience())
                    .withIssuedAt(new Date(now))
                    .withExpiresAt(expiresAt)
                    .sign(algorithm);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 校验 token：签名、iss/aud/exp 均会校验。
     *
     * @param token         JWT 字符串
     * @param userName      期望的用户名
     * @param sessionSecret 会话密钥（从 Redis 等获取）
     * @return 是否校验通过
     */
    public boolean verify(String token, String userName, String sessionSecret) {
        if (token == null || userName == null || sessionSecret == null) {
            return false;
        }
        try {
            Algorithm algorithm = Algorithm.HMAC384(signingKey(sessionSecret));
            JWTVerifier verifier = JWT.require(algorithm)
                    .withClaim(CLAIM_USER_NAME, userName)
                    .withIssuer(issuer())
                    .withAudience(audience())
                    .build();
            verifier.verify(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 token 中解析出 userName（仅解码，不校验签名；实际鉴权在拦截器中用 verify）。
     *
     * @param token JWT 字符串
     * @return userName，解析失败返回 null
     */
    public String getUsername(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            DecodedJWT decoded = JWT.decode(token);
            return decoded.getClaim(CLAIM_USER_NAME).asString();
        } catch (JWTDecodeException e) {
            return null;
        }
    }

    /**
     * 获取配置的过期时间（分钟），用于与 Redis 会话 TTL 一致。
     */
    public int getExpireMinutes() {
        return zhongtaiConfigService.getInt(SYSTEM_CODE, KEY_EXPIRE_MINUTES);
    }

    private String serverSecret() {
        return zhongtaiConfigService.getString(SYSTEM_CODE, KEY_SERVER_SECRET);
    }

    private String issuer() {
        return zhongtaiConfigService.getString(SYSTEM_CODE, KEY_ISSUER);
    }

    private String audience() {
        return zhongtaiConfigService.getString(SYSTEM_CODE, KEY_AUDIENCE);
    }
}

package com.common.notify.feishu;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import com.common.Util.Redis.RedisClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 飞书 tenant_access_token 管理器。
 * 优先读 Redis，未命中时再向飞书申请新 token，避免每次发送消息都触发鉴权请求。
 * 若业务模块没有注入 RedisClient，也允许直接降级为“无缓存申请 token”，保证通知能力不阻塞服务启动。
 */
@Slf4j
public class FeishuTenantAccessTokenService {

    private static final int DEFAULT_TOKEN_TTL_SECONDS = 7200;
    private static final int TOKEN_REFRESH_BUFFER_SECONDS = 300;

    private final FeishuNotifyProperties properties;
    @Nullable
    private final RedisClient redisClient;
    private final ObjectMapper objectMapper;

    public FeishuTenantAccessTokenService(FeishuNotifyProperties properties,
                                          @Nullable RedisClient redisClient,
                                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisClient = redisClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取当前可用的 tenant_access_token。
     * 若 Redis 不可用或远端申请失败，返回 null，让上层发送逻辑做安全降级。
     */
    public String getTenantAccessToken() {
        return getTenantAccessToken(properties.getAppId(), properties.getAppSecret());
    }

    /**
     * 按指定应用凭证获取 tenant_access_token。
     * <p>
     * 中台通知通道允许后续按业务场景拆分飞书机器人，因此 token 缓存键需要跟随 appId 隔离。
     * </p>
     *
     * @param appId 飞书应用 appId
     * @param appSecret 飞书应用 appSecret
     * @return tenant_access_token，获取失败时返回 null
     */
    public String getTenantAccessToken(String appId, String appSecret) {
        if (isBlank(appId) || isBlank(appSecret)) {
            return null;
        }
        String cacheKey = buildTokenCacheKey(appId);
        if (redisClient != null) {
            try {
                Object cachedValue = redisClient.getCacheObject(cacheKey);
                if (cachedValue != null) {
                    String token = String.valueOf(cachedValue);
                    if (!token.trim().isEmpty()) {
                        return token;
                    }
                }
            } catch (Exception e) {
                log.warn("读取飞书 token 缓存失败: {}", e.getMessage());
            }
        }
        return refreshToken(cacheKey, appId, appSecret);
    }

    private String refreshToken(String cacheKey, String appId, String appSecret) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("app_id", appId);
            body.put("app_secret", appSecret);
            String responseBody = HttpRequest.post(buildTokenUrl())
                    .header("Content-Type", ContentType.JSON.getValue())
                    .body(objectMapper.writeValueAsString(body))
                    .timeout(5000)
                    .execute()
                    .body();
            Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            Object code = response.get("code");
            if (code instanceof Number && ((Number) code).intValue() != 0) {
                log.warn("申请飞书 tenant_access_token 失败: code={}, msg={}", code, response.get("msg"));
                return null;
            }
            String token = response.get("tenant_access_token") == null ? null : String.valueOf(response.get("tenant_access_token"));
            if (token == null || token.trim().isEmpty()) {
                log.warn("申请飞书 tenant_access_token 失败: 响应缺少 token");
                return null;
            }
            int expire = response.get("expire") instanceof Number
                    ? ((Number) response.get("expire")).intValue()
                    : DEFAULT_TOKEN_TTL_SECONDS;
            int cacheSeconds = Math.max(60, expire - TOKEN_REFRESH_BUFFER_SECONDS);
            if (redisClient != null) {
                try {
                    redisClient.setCacheObject(cacheKey, token, cacheSeconds, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("写入飞书 token 缓存失败: {}", e.getMessage());
                }
            }
            return token;
        } catch (Exception e) {
            log.error("刷新飞书 tenant_access_token 失败", e);
            return null;
        }
    }

    private String buildTokenCacheKey(String appId) {
        return "feishu:tenant_access_token:" + appId;
    }

    private String buildTokenUrl() {
        return trimSlash(properties.getBaseUrl()) + "/open-apis/auth/v3/tenant_access_token/internal";
    }

    private String trimSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

package com.common.notify.feishu;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 飞书消息发送客户端。
 * 统一封装 interactive 卡片请求，避免业务层直接关心 HTTP 协议细节。
 */
@Slf4j
public class FeishuMessageClient {

    private final FeishuNotifyProperties properties;
    private final FeishuTenantAccessTokenService accessTokenService;
    private final ObjectMapper objectMapper;

    public FeishuMessageClient(FeishuNotifyProperties properties,
                               FeishuTenantAccessTokenService accessTokenService,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.accessTokenService = accessTokenService;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送 interactive 卡片消息。
     *
     * @param receiveId 接收方 ID
     * @param cardContent 已经序列化好的卡片 JSON 字符串
     * @param uuid 业务幂等标识，便于飞书侧去重
     * @return 发送是否成功
     */
    public boolean sendInteractiveCard(String receiveId, String cardContent, String uuid) {
        return sendInteractiveCard(receiveId, properties.getReceiveIdType(), properties.getAppId(),
                properties.getAppSecret(), cardContent, uuid);
    }

    /**
     * 按中台通知通道发送 interactive 卡片。
     * <p>
     * 业务场景拆分飞书机器人或接收群时，调用方传入中台通道快照中的应用凭证与接收配置；
     * 未拆分时继续走默认配置。
     * </p>
     *
     * @param receiveId 接收方 ID
     * @param receiveIdType 接收方类型
     * @param appId 飞书应用 appId
     * @param appSecret 飞书应用 appSecret
     * @param cardContent 已经序列化好的卡片 JSON 字符串
     * @param uuid 业务幂等标识，便于飞书侧去重
     * @return 发送是否成功
     */
    public boolean sendInteractiveCard(String receiveId,
                                       String receiveIdType,
                                       String appId,
                                       String appSecret,
                                       String cardContent,
                                       String uuid) {
        String token = accessTokenService.getTenantAccessToken(appId, appSecret);
        if (token == null || token.trim().isEmpty()) {
            log.warn("发送飞书卡片失败: tenant_access_token 不可用");
            return false;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("receive_id", receiveId);
            body.put("msg_type", "interactive");
            body.put("content", cardContent);
            if (uuid != null && !uuid.trim().isEmpty()) {
                body.put("uuid", uuid);
            }
            String responseBody = HttpRequest.post(buildMessageUrl(receiveIdType))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", ContentType.JSON.getValue())
                    .body(objectMapper.writeValueAsString(body))
                    .timeout(5000)
                    .execute()
                    .body();
            Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            Object code = response.get("code");
            boolean success = !(code instanceof Number) || ((Number) code).intValue() == 0;
            if (!success) {
                log.warn("发送飞书卡片失败: code={}, msg={}", code, response.get("msg"));
            }
            return success;
        } catch (Exception e) {
            log.error("发送飞书卡片异常", e);
            return false;
        }
    }

    private String buildMessageUrl(String receiveIdType) {
        return trimSlash(properties.getBaseUrl())
                + "/open-apis/im/v1/messages?receive_id_type=" + receiveIdType;
    }

    private String trimSlash(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}

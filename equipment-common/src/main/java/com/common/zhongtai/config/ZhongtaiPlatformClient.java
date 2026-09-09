package com.common.zhongtai.config;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * 中台服务 HTTP 客户端。
 * <p>
 * 业务服务读取中台配置时走该客户端，保持微服务边界清晰。
 * </p>
 */
public class ZhongtaiPlatformClient {

    private final ZhongtaiClientProperties properties;
    private final RestTemplate restTemplate;

    /**
     * 创建中台 HTTP 客户端。
     *
     * @param properties 客户端配置
     */
    public ZhongtaiPlatformClient(ZhongtaiClientProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeoutMillis());
        requestFactory.setReadTimeout(properties.getTimeoutMillis());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    /**
     * 判断客户端是否可用。
     *
     * @return true 表示已配置基础地址
     */
    public boolean isAvailable() {
        return properties.isEnabled() && StringUtils.hasText(properties.getBaseUrl());
    }

    /**
     * 读取中台配置值。
     *
     * @param systemCode 系统编码
     * @param configKey 配置键
     * @return 配置值，不存在时返回 null
     */
    public String getConfigValue(String systemCode, String configKey) {
        Map response = restTemplate.getForObject(buildUrl("/api/platform/internal/config/value")
                        .queryParam("systemCode", systemCode)
                        .queryParam("configKey", configKey)
                        .toUriString(),
                Map.class);
        Object data = response == null ? null : response.get("data");
        return data == null ? null : String.valueOf(data);
    }

    /**
     * 查询中台配置列表。
     *
     * @param systemCode 系统编码
     * @param configGroup 配置分组
     * @return 配置列表，敏感值由中台按管理页规则处理
     */
    public List<Map<String, Object>> listConfigs(String systemCode, String configGroup) {
        Map response = restTemplate.getForObject(buildUrl("/api/platform/internal/config/list")
                        .queryParam("systemCode", systemCode)
                        .queryParam("configGroup", configGroup)
                        .toUriString(),
                Map.class);
        Object data = response == null ? null : response.get("data");
        return data instanceof List ? (List<Map<String, Object>>) data : java.util.Collections.emptyList();
    }

    /**
     * 保存中台配置。
     *
     * @param config 配置字段 Map，字段名保持与 PlatformConfig Java 属性一致
     */
    public void saveConfig(Map<String, Object> config) {
        restTemplate.postForObject(buildUrl("/api/platform/internal/config/save").toUriString(), config, Map.class);
    }

    /**
     * 读取中台通知通道。
     *
     * @param systemCode 系统编码
     * @param sceneCode 场景编码
     * @param channelCode 通道编码
     * @return 通知通道快照
     */
    public ZhongtaiNotifyChannelService.ChannelSnapshot getNotifyChannel(String systemCode,
                                                                         String sceneCode,
                                                                         String channelCode) {
        Map response = restTemplate.getForObject(buildUrl("/api/platform/internal/notify-channel")
                        .queryParam("systemCode", systemCode)
                        .queryParam("sceneCode", sceneCode)
                        .queryParam("channelCode", channelCode)
                        .toUriString(),
                Map.class);
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof Map)) {
            return null;
        }
        Map dataMap = (Map) data;
        ZhongtaiNotifyChannelService.ChannelSnapshot snapshot = new ZhongtaiNotifyChannelService.ChannelSnapshot();
        snapshot.setAppId(value(dataMap.get("appId")));
        snapshot.setAppSecret(value(dataMap.get("appSecret")));
        snapshot.setReceiveIdType(value(dataMap.get("receiveIdType")));
        snapshot.setReceiveId(value(dataMap.get("receiveId")));
        return snapshot;
    }

    /**
     * 读取中台任务配置。
     *
     * @param systemCode 系统编码
     * @param taskCode 任务编码
     * @return 任务配置
     */
    public ZhongtaiScheduleTaskConfig getScheduleTask(String systemCode, String taskCode) {
        Map response = restTemplate.getForObject(buildUrl("/api/platform/internal/schedule-task")
                        .queryParam("systemCode", systemCode)
                        .queryParam("taskCode", taskCode)
                        .toUriString(),
                Map.class);
        Object data = response == null ? null : response.get("data");
        if (!(data instanceof Map)) {
            return null;
        }
        Map dataMap = (Map) data;
        ZhongtaiScheduleTaskConfig config = new ZhongtaiScheduleTaskConfig();
        config.setCronExpr(value(dataMap.get("cronExpr")));
        Object enabled = dataMap.get("enabled");
        config.setEnabled(Boolean.TRUE.equals(enabled)
                || Integer.valueOf(1).equals(enabled)
                || "true".equalsIgnoreCase(value(enabled)));
        return config;
    }

    private UriComponentsBuilder buildUrl(String path) {
        return UriComponentsBuilder.fromHttpUrl(trimSlash(properties.getBaseUrl()) + path);
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String value(Object rawValue) {
        return rawValue == null ? null : String.valueOf(rawValue);
    }
}

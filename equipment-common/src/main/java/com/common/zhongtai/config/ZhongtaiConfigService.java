package com.common.zhongtai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中台配置读取服务。
 * <p>
 * 业务系统通过该服务访问中台配置接口读取配置。中台配置已经作为业务配置唯一来源，
 * 读取失败、表未初始化或必需配置不存在时直接抛错，避免继续使用旧 yml 或硬编码值。
 * </p>
 */
@Slf4j
public class ZhongtaiConfigService {

    private static final long CACHE_TTL_MS = 30_000L;
    private static final String GLOBAL_SYSTEM = "global";

    private final ObjectProvider<ZhongtaiPlatformClient> platformClientProvider;
    private final Map<String, CacheItem> cache = new ConcurrentHashMap<>();

    /**
     * 创建中台配置读取服务。
     *
     * @param platformClientProvider 中台服务 HTTP 客户端提供器
     */
    public ZhongtaiConfigService(ObjectProvider<ZhongtaiPlatformClient> platformClientProvider) {
        this.platformClientProvider = platformClientProvider;
    }

    /**
     * 读取字符串配置。
     *
     * @param systemCode 系统编码，例如 alipay、common、global
     * @param configKey 配置键
     * @return 配置值
     */
    public String getString(String systemCode, String configKey) {
        String value = getOptionalString(systemCode, configKey);
        if (value == null) {
            throw new IllegalStateException("中台配置缺失: systemCode=" + systemCode + ", configKey=" + configKey);
        }
        return value;
    }

    /**
     * 读取可选字符串配置。
     * <p>
     * 只用于确实允许为空的配置项，例如日志白名单、可选跳转模板等；业务主配置不要使用该方法绕过中台校验。
     * </p>
     *
     * @param systemCode 系统编码
     * @param configKey 配置键
     * @return 配置值，不存在时返回 null
     */
    public String getOptionalString(String systemCode, String configKey) {
        if (!StringUtils.hasText(configKey)) {
            throw new IllegalArgumentException("中台配置键不能为空");
        }
        String normalizedSystem = StringUtils.hasText(systemCode) ? systemCode.trim() : GLOBAL_SYSTEM;
        String value = readCached(normalizedSystem, configKey.trim());
        if (value != null) {
            return value;
        }
        if (!GLOBAL_SYSTEM.equals(normalizedSystem)) {
            value = readCached(GLOBAL_SYSTEM, configKey.trim());
        }
        return value;
    }

    /**
     * 读取布尔配置。
     *
     * @param systemCode 系统编码
     * @param configKey 配置键
     * @return 布尔配置值
     */
    public boolean getBoolean(String systemCode, String configKey) {
        String value = getString(systemCode, configKey);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("中台布尔配置为空: systemCode=" + systemCode + ", configKey=" + configKey);
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()) || "yes".equalsIgnoreCase(value.trim());
    }

    /**
     * 读取整数配置。
     *
     * @param systemCode 系统编码
     * @param configKey 配置键
     * @return 整数配置值
     */
    public int getInt(String systemCode, String configKey) {
        String value = getString(systemCode, configKey);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("中台整数配置为空: systemCode=" + systemCode + ", configKey=" + configKey);
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("中台配置不是合法整数: systemCode=" + systemCode + ", configKey=" + configKey, e);
        }
    }

    private String readCached(String systemCode, String configKey) {
        String cacheKey = systemCode + ":" + configKey;
        CacheItem item = cache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (item != null && now - item.getLoadedAt() < CACHE_TTL_MS) {
            return item.getValue();
        }
        String value = readFromPlatform(systemCode, configKey);
        cache.put(cacheKey, new CacheItem(value, now));
        return value;
    }

    /**
     * 从中台服务读取配置。
     *
     * @param systemCode 系统编码
     * @param configKey 配置键
     * @return 配置值
     */
    private String readFromPlatform(String systemCode, String configKey) {
        ZhongtaiPlatformClient client = platformClientProvider == null ? null : platformClientProvider.getIfAvailable();
        if (client == null || !client.isAvailable()) {
            throw new IllegalStateException("未配置中台服务地址: zhongtai.client.base-url");
        }
        try {
            return client.getConfigValue(systemCode, configKey);
        } catch (Exception e) {
            throw new IllegalStateException("从中台服务读取配置失败: systemCode=" + systemCode + ", configKey=" + configKey, e);
        }
    }

    /**
     * 配置缓存项。
     */
    private static class CacheItem {
        private final String value;
        private final long loadedAt;

        private CacheItem(String value, long loadedAt) {
            this.value = value;
            this.loadedAt = loadedAt;
        }

        private String getValue() {
            return value;
        }

        private long getLoadedAt() {
            return loadedAt;
        }
    }
}

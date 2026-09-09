package com.common.zhongtai.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中台通知通道读取服务。
 * <p>
 * 当前用于飞书默认机器人与默认接收群读取。后续按业务场景拆分机器人时，可继续按
 * systemCode、sceneCode、channelCode 读取不同通道。
 * </p>
 */
@Slf4j
public class ZhongtaiNotifyChannelService {

    private static final long CACHE_TTL_MS = 30_000L;

    private final ObjectProvider<ZhongtaiPlatformClient> platformClientProvider;
    private final Map<String, CacheItem> cache = new ConcurrentHashMap<>();

    /**
     * 创建中台通知通道读取服务。
     *
     * @param platformClientProvider 中台服务 HTTP 客户端提供器
     */
    public ZhongtaiNotifyChannelService(ObjectProvider<ZhongtaiPlatformClient> platformClientProvider) {
        this.platformClientProvider = platformClientProvider;
    }

    /**
     * 读取默认飞书通道。
     *
     * @return 默认飞书通道，不存在时返回空对象
     */
    public ChannelSnapshot defaultFeishuChannel() {
        return getChannel("common", "default", "feishu.default");
    }

    /**
     * 读取指定通知通道。
     *
     * @param systemCode 系统编码
     * @param sceneCode 场景编码
     * @param channelCode 通道编码
     * @return 通知通道快照
     */
    public ChannelSnapshot getChannel(String systemCode, String sceneCode, String channelCode) {
        String key = systemCode + ":" + sceneCode + ":" + channelCode;
        long now = System.currentTimeMillis();
        CacheItem item = cache.get(key);
        if (item != null && now - item.getLoadedAt() < CACHE_TTL_MS) {
            return item.getSnapshot();
        }
        ChannelSnapshot snapshot = readFromPlatform(systemCode, sceneCode, channelCode);
        // 未配置通道时缓存空快照，避免中台不可用或未配置场景在 TTL 内反复打日志。
        cache.put(key, new CacheItem(snapshot, now));
        return snapshot;
    }

    /**
     * 从中台服务读取通知通道。
     * <p>
     * 飞书通知是异步旁路能力，通道未配置或中台不可用时不能反向影响主流程，
     * 因此这里统一返回空快照（hasReceiver 为 false），由上层判定是否跳过发送。
     * </p>
     */
    private ChannelSnapshot readFromPlatform(String systemCode, String sceneCode, String channelCode) {
        ZhongtaiPlatformClient client = platformClientProvider == null ? null : platformClientProvider.getIfAvailable();
        if (client == null || !client.isAvailable()) {
            log.debug("中台服务未配置，跳过通知通道读取: zhongtai.client.base-url");
            return new ChannelSnapshot();
        }
        try {
            ChannelSnapshot snapshot = client.getNotifyChannel(systemCode, sceneCode, channelCode);
            if (snapshot == null) {
                log.debug("中台通知通道未配置，跳过通知: systemCode={}, sceneCode={}, channelCode={}",
                        systemCode, sceneCode, channelCode);
                return new ChannelSnapshot();
            }
            return snapshot;
        } catch (Exception e) {
            // 中台读取异常只记日志，返回空快照，保证主流程不被通知能力拖垮。
            log.warn("从中台服务读取通知通道失败，已跳过通知: systemCode={}, sceneCode={}, channelCode={}",
                    systemCode, sceneCode, channelCode, e);
            return new ChannelSnapshot();
        }
    }

    /**
     * 通知通道快照。
     */
    @Data
    public static class ChannelSnapshot {
        private String appId;
        private String appSecret;
        private String receiveIdType;
        private String receiveId;

        /**
         * 判断快照是否有可用接收者配置。
         *
         * @return 是否有接收者
         */
        public boolean hasReceiver() {
            return StringUtils.hasText(receiveIdType) && StringUtils.hasText(receiveId);
        }
    }

    private static class CacheItem {
        private final ChannelSnapshot snapshot;
        private final long loadedAt;

        private CacheItem(ChannelSnapshot snapshot, long loadedAt) {
            this.snapshot = snapshot;
            this.loadedAt = loadedAt;
        }

        private ChannelSnapshot getSnapshot() {
            return snapshot;
        }

        private long getLoadedAt() {
            return loadedAt;
        }
    }
}

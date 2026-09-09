package com.fly.rent.common.support;

/**
 * 新租赁接口使用到的 Redis key 前缀。
 * 集中定义可以避免魔法字符串散落在多个服务里。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public final class RentRedisKeys {

    /** 用户扩展信息前缀 */
    public static final String USER_PROFILE_EXTRA_PREFIX = "rent:user:profile:extra:";
    /** 订单扩展信息前缀 */
    public static final String ORDER_EXTENSION_PREFIX = "rent:order:extension:";
    /** 售后单旧版单条快照前缀，兼容历史数据读取 */
    public static final String ORDER_BACK_PREFIX = "rent:order:back:";
    /** 售后单最新快照前缀 */
    public static final String ORDER_BACK_LATEST_PREFIX = "rent:order:back:latest:";
    /** 售后单历史记录前缀 */
    public static final String ORDER_BACK_HISTORY_PREFIX = "rent:order:back:history:";
    /** 刷新令牌前缀 */
    public static final String AUTH_REFRESH_PREFIX = "rent:auth:refresh:";

    /** 小程序热点缓存前缀（防雪崩/击穿/穿透，5分钟TTL） */
    public static final String CACHE_PREFIX = "rent:cache:";
    /** 缓存加载锁前缀（防击穿） */
    public static final String CACHE_LOCK_PREFIX = "rent:lock:cache:";
    /** 空值占位缓存 TTL（防穿透），秒 */
    public static final int CACHE_NULL_TTL_SECONDS = 60;
    /** 默认缓存 TTL 分钟 */
    public static final int CACHE_TTL_MINUTES = 5;
    /** 缓存加载锁持有时间，秒 */
    public static final int CACHE_LOCK_LEASE_SECONDS = 30;

    private RentRedisKeys() {
    }
}

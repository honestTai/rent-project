package com.fly.rent.miniapp.cache;

import com.fly.rent.config.RedisClient;
import com.fly.rent.common.support.RentRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 小程序热点缓存：防雪崩（TTL+随机）、防击穿（分布式锁）、防穿透（空值短TTL）。
 * 默认 5 分钟 TTL，可定时刷新或按需失效。缓存异常时一律走 DB 兜底。
 *
 * @author HonestTat
 * @since 2026-03-15
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MiniappCacheService {

    private static final String NULL_PLACEHOLDER_KEY_SUFFIX = ":empty";
    private static final int RETRY_AFTER_LOCK_FAIL_MS = 50;
    private static final int RETRY_TIMES = 20;

    private final RedisClient redisClient;

    /**
     * 从缓存获取或加载：防雪崩（TTL+0~60s随机）、防击穿（未命中时加锁加载）、防穿透（可选缓存空值短TTL）。
     * 任意 Redis 异常时直接走 loader 查库兜底，不抛错。
     *
     * @param key       缓存键（会加前缀 rent:cache:）
     * @param loader    未命中时加载数据的逻辑（数据库兜底）
     * @param cacheNull 是否缓存“不存在”（防穿透），true 时 loader 返回 null 会缓存占位 60s
     * @return 缓存或加载得到的值，可能为 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Supplier<T> loader, boolean cacheNull) {
        return getOrLoad(key, loader, cacheNull, null, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Supplier<T> loader, boolean cacheNull, Integer timeout, TimeUnit timeUnit) {
        String fullKey = RentRedisKeys.CACHE_PREFIX + key;
        String emptyKey = fullKey + NULL_PLACEHOLDER_KEY_SUFFIX;
        String lockKey = RentRedisKeys.CACHE_LOCK_PREFIX + key;

        try {
            // 1) 先查主键
            T cached = (T) redisClient.getCacheObject(fullKey);
            if (cached != null) {
                return cached;
            }
            // 2) 再查空值占位（防穿透）
            Object emptyMark = redisClient.getCacheObject(emptyKey);
            if (emptyMark != null) {
                return null;
            }
        } catch (Exception e) {
            log.warn("缓存读取失败，走数据库兜底 key={}: {}", key, e.getMessage());
            return loader.get();
        }

        // 3) 未命中：加锁加载（防击穿）
        String lockValue = lockKey + ":" + System.currentTimeMillis();
        boolean locked = false;
        try {
            locked = redisClient.tryLock(lockKey, lockValue,
                    RentRedisKeys.CACHE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存加锁失败，走数据库兜底 key={}: {}", key, e.getMessage());
            return loader.get();
        }

        try {
            if (locked) {
                try {
                    // 双重检查
                    T cached = (T) redisClient.getCacheObject(fullKey);
                    if (cached != null) {
                        return cached;
                    }
                    if (redisClient.getCacheObject(emptyKey) != null) {
                        return null;
                    }
                } catch (Exception e) {
                    log.warn("缓存二次读取失败，走数据库兜底 key={}: {}", key, e.getMessage());
                    return loader.get();
                }
                T value = loader.get();
                try {
                    if (value == null) {
                        if (cacheNull) {
                            redisClient.setCacheObject(emptyKey, Boolean.TRUE,
                                    RentRedisKeys.CACHE_NULL_TTL_SECONDS, TimeUnit.SECONDS);
                        }
                        return null;
                    }
                    int ttlSeconds = ttlSecondsWithRandom(timeout, timeUnit);
                    redisClient.setCacheObject(fullKey, value, ttlSeconds, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.warn("缓存写入失败，已返回 DB 结果 key={}: {}", key, e.getMessage());
                }
                return value;
            }
        } finally {
            if (locked) {
                try {
                    redisClient.releaseLock(lockKey, lockValue);
                } catch (Exception e) {
                    log.warn("释放缓存锁失败 key={}: {}", key, e.getMessage());
                }
            }
        }

        // 未拿到锁：短暂等待后重试从缓存读，仍失败则走 DB
        for (int i = 0; i < RETRY_TIMES; i++) {
            try {
                Thread.sleep(RETRY_AFTER_LOCK_FAIL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return loader.get();
            }
            try {
                T cached = (T) redisClient.getCacheObject(fullKey);
                if (cached != null) {
                    return cached;
                }
                if (redisClient.getCacheObject(emptyKey) != null) {
                    return null;
                }
            } catch (Exception e) {
                return loader.get();
            }
        }
        return loader.get();
    }

    /**
     * 不缓存空值版本的 getOrLoad（适合“必然存在”的 key，如当前用户资料）。
     */
    public <T> T getOrLoad(String key, Supplier<T> loader) {
        return getOrLoad(key, loader, false);
    }

    /**
     * 主动写入缓存（用于定时刷新）：写入后 TTL 为 5 分钟 + 随机 0~60 秒。失败只打日志，不抛错。
     */
    public void put(String key, Object value) {
        put(key, value, null, null);
    }

    public void put(String key, Object value, Integer timeout, TimeUnit timeUnit) {
        if (value == null) {
            return;
        }
        try {
            String fullKey = RentRedisKeys.CACHE_PREFIX + key;
            int ttlSeconds = ttlSecondsWithRandom(timeout, timeUnit);
            redisClient.setCacheObject(fullKey, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("缓存 put 失败 key={}: {}", key, e.getMessage());
        }
    }

    /**
     * 删除缓存（更新码表后调用，保证下次读到的为新值）。失败只打日志，不影响业务。
     */
    public void evict(String key) {
        try {
            String fullKey = RentRedisKeys.CACHE_PREFIX + key;
            String emptyKey = fullKey + NULL_PLACEHOLDER_KEY_SUFFIX;
            redisClient.deleteObject(fullKey);
            redisClient.deleteObject(emptyKey);
        } catch (Exception e) {
            log.warn("缓存 evict 失败 key={}: {}", key, e.getMessage());
        }
    }

    /** 5 分钟 + 0~60 秒随机，防雪崩 */
    private int ttlSecondsWithRandom() {
        return ttlSecondsWithRandom(null, null);
    }

    private int ttlSecondsWithRandom(Integer timeout, TimeUnit timeUnit) {
        if (timeout != null && timeUnit != null) {
            int base = (int) Math.max(1L, timeUnit.toSeconds(timeout.longValue()));
            int random = base >= 300 ? (int) (Math.random() * 301) : Math.max(1, base / 10);
            return base + random;
        }
        int base = RentRedisKeys.CACHE_TTL_MINUTES * 60;
        int random = (int) (Math.random() * 61);
        return base + random;
    }
}

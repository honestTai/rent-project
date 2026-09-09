package com.fly.rent.config;

import org.redisson.api.*;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * redis链接类 (Redisson 实现)
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Component
public class RedisClient {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key   缓存的键值
     * @param value 缓存的值
     */
    public <T> void setCacheObject(String key, T value) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value);
    }

    /**
     * 缓存基本的对象，Integer、String、实体类等
     *
     * @param key      缓存的键值
     * @param value    缓存的值
     * @param timeout  时间
     * @param timeUnit 时间颗粒度
     */
    public <T> void setCacheObject(String key, T value, Integer timeout, TimeUnit timeUnit) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value, timeout, timeUnit);
    }

    /**
     * 获得缓存的基本对象。
     * 若 Redis 中为旧格式（如 Marshalling 协议不兼容）导致反序列化失败，返回 null，避免抛错。
     *
     * @param key 缓存键值
     * @return 缓存键值对应的数据
     */
    public <T> T getCacheObject(String key) {
        try {
            RBucket<T> bucket = redissonClient.getBucket(key);
            return bucket.get();
        } catch (RedisException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException
                    || (e.getMessage() != null && e.getMessage().contains("Unsupported protocol"))) {
                return null;
            }
            throw e;
        }
    }

    /**
     * 批量获得缓存对象，顺序与 keys 一致，缺失的 key 对应位置为 null。
     *
     * @param keys 缓存键值集合
     * @return 与 keys 顺序对应的值列表
     */
    public List<Object> getCacheObjects(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        // Redisson 的 mget 需要所有 key 对应的 bucket 都在同一个 slot 或者使用 batch，
        // 但简单的做法是循环 get 或者使用 RBatch。
        // RBuckets 接口提供了 get 方法
        RBuckets buckets = redissonClient.getBuckets();
        Map<String, Object> result = buckets.get(keys.toArray(new String[0]));
        
        // 保持顺序
        List<Object> list = new ArrayList<>();
        for (String key : keys) {
            list.add(result.get(key));
        }
        return list;
    }

    /**
     * 删除单个对象
     *
     * @param key 键
     */
    public void deleteObject(String key) {
        redissonClient.getBucket(key).delete();
    }

    /**
     * 删除集合中的键对应对象
     *
     * @param keys 键集合，非 String 元素会被忽略
     */
    public void deleteObject(Collection<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                redissonClient.getBucket(key).delete();
            }
        }
    }

    /**
     * 更新过期时间
     *
     * @param key      缓存的键值
     * @param timeout  时间
     * @param timeUnit 时间颗粒度
     * @return 是否成功
     */
    public boolean expire(String key, Integer timeout, TimeUnit timeUnit) {
        Duration duration = Duration.ofMillis(timeUnit.toMillis(timeout.longValue()));
        return redissonClient.getBucket(key).expire(duration);
    }

    /**
     * 自增
     * @param key 键
     * @param delta 增量
     * @return 结果
     */
    public Long increment(String key, long delta) {
        return redissonClient.getAtomicLong(key).addAndGet(delta);
    }

    /**
     * 获取过期时间
     * @param key 键
     * @param timeUnit 时间单位
     * @return 过期时间
     */
    public Long getExpire(String key, TimeUnit timeUnit) {
        long remain = redissonClient.getBucket(key).remainTimeToLive();
        if (remain < 0) {
            return remain;
        }
        return timeUnit.convert(remain, TimeUnit.MILLISECONDS);
    }

    /**
     * 尝试获取分布式锁
     *
     * @param key      锁的键值
     * @param value    锁的值（Redisson 忽略此值，使用线程ID）
     * @param timeout  锁的过期时间（Lease Time）
     * @param timeUnit 时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String key, String value, Integer timeout, TimeUnit timeUnit) {
        RLock lock = redissonClient.getLock(key);
        try {
            // tryLock(waitTime, leaseTime, unit)
            // waitTime = 0 表示不等待，立即返回
            return lock.tryLock(0, timeout, timeUnit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放分布式锁
     *
     * @param key   锁的键值
     * @param value 锁的值（Redisson 忽略此值）
     * @return 是否释放成功
     */
    public boolean releaseLock(String key, String value) {
        RLock lock = redissonClient.getLock(key);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            return true;
        }
        return false;
    }
}

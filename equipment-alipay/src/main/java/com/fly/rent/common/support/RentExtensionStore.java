package com.fly.rent.common.support;

import com.fly.rent.common.model.RentOrderExtension;
import com.fly.rent.common.model.RentUserProfileExtra;
import com.fly.rent.config.RedisClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 新接口扩展字段的统一存取层。
 * 先把新增字段沉淀在这里，后续无论改 Redis 还是迁数据库，都只需要改这一层。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@RequiredArgsConstructor
@Component
public class RentExtensionStore {

    private final RedisClient redisClient;

    /**
     * 读取用户扩展资料。
     * 不存在时返回默认对象，避免上层反复判空。
     * @param userUuid 用户UUID
     * @return 用户扩展资料
     */
    public RentUserProfileExtra loadUserProfileExtra(String userUuid) {
        RentUserProfileExtra extra = redisClient.getCacheObject(RentRedisKeys.USER_PROFILE_EXTRA_PREFIX + userUuid);
        return extra == null ? new RentUserProfileExtra() : extra;
    }

    /**
     * 保存用户扩展资料
     * @param userUuid 用户UUID
     * @param extra 用户扩展资料
     */
    public void saveUserProfileExtra(String userUuid, RentUserProfileExtra extra) {
        redisClient.setCacheObject(RentRedisKeys.USER_PROFILE_EXTRA_PREFIX + userUuid, extra);
    }

    /**
     * 读取订单扩展信息。
     * @param orderNo 订单号
     * @return 订单扩展信息
     */
    public RentOrderExtension loadOrderExtension(String orderNo) {
        return redisClient.getCacheObject(RentRedisKeys.ORDER_EXTENSION_PREFIX + orderNo);
    }

    /**
     * 批量读取订单扩展信息，用于列表接口减少 Redis 往返。
     * @param orderNos 订单号列表
     * @return orderNo -> 扩展信息，不存在的订单不在 map 中
     */
    public Map<String, RentOrderExtension> loadOrderExtensions(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> keys = orderNos.stream()
                .map(no -> RentRedisKeys.ORDER_EXTENSION_PREFIX + no)
                .collect(Collectors.toList());
        List<Object> values = redisClient.getCacheObjects(keys);
        Map<String, RentOrderExtension> map = new HashMap<>(orderNos.size());
        for (int i = 0; i < orderNos.size(); i++) {
            Object v = (values != null && i < values.size()) ? values.get(i) : null;
            if (v instanceof RentOrderExtension) {
                map.put(orderNos.get(i), (RentOrderExtension) v);
            }
        }
        return map;
    }

    /**
     * 保存订单扩展信息
     * @param orderNo 订单号
     * @param extension 订单扩展信息
     */
    public void saveOrderExtension(String orderNo, RentOrderExtension extension) {
        redisClient.setCacheObject(RentRedisKeys.ORDER_EXTENSION_PREFIX + orderNo, extension);
    }

    /**
     * 保存刷新令牌。
     * 当前项目还没有单独刷新接口，但先把数据规范好，后续扩展时可以直接接上。
     * @param refreshToken 刷新令牌
     * @param userName 用户名
     */
    public void saveRefreshToken(String refreshToken, String userName) {
        redisClient.setCacheObject(
                RentRedisKeys.AUTH_REFRESH_PREFIX + refreshToken,
                userName,
                30,
                TimeUnit.DAYS
        );
    }
}

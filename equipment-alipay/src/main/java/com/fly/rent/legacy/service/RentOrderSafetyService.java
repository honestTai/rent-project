package com.fly.rent.legacy.service;

import com.fly.rent.entity.Order;
import com.alipay.api.AlipayApiException;

/**
 * 租赁订单安全保障服务。
 * 集中处理分布式锁、下单限流、支付前校验和超时关单等兜底逻辑。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public interface RentOrderSafetyService {

    /**
     * 获取用户维度的下单锁。
     * @param userUuid 用户UUID
     * @return 锁的值
     */
    String acquireCreateLock(String userUuid);

    /**
     * 释放用户维度的下单锁。
     * @param userUuid 用户UUID
     * @param lockValue 锁的值
     */
    void releaseCreateLock(String userUuid, String lockValue);

    /**
     * 校验当前用户是否允许继续创建订单。
     * @param userUuid 用户UUID
     * @throws AlipayApiException 支付宝异常
     */
    void ensureCreateAllowed(String userUuid) throws AlipayApiException;

    /**
     * 校验当前用户是否允许继续创建续租订单。
     * 续租订单与普通租赁订单使用独立的未支付数量和频率限制，避免续租子单阻断用户继续发起普通租赁。
     * @param userUuid 用户UUID
     * @throws AlipayApiException 支付宝异常
     */
    void ensureReletCreateAllowed(String userUuid) throws AlipayApiException;

    /**
     * 校验订单是否允许继续支付。
     * @param order 订单
     * @throws AlipayApiException 支付宝异常
     */
    void ensurePayable(Order order) throws AlipayApiException;

    /**
     * 扫描并关闭全量超时未支付订单。
     * @throws AlipayApiException 支付宝异常
     */
    void closeExpiredUnpaidOrders() throws AlipayApiException;

    /**
     * 扫描并关闭指定用户的超时未支付订单。
     * @param userUuid 用户UUID
     * @throws AlipayApiException 支付宝异常
     */
    void closeExpiredUnpaidOrders(String userUuid) throws AlipayApiException;
}

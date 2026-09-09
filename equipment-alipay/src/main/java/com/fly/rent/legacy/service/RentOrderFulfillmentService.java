package com.fly.rent.legacy.service;

import com.fly.rent.entity.Order;
import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.FulfillmentDeliveryInfo;

import java.util.List;

/**
 * 租赁订单履约服务。
 * 负责发货、收货、完结等和物流/履约相关的状态流转。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public interface RentOrderFulfillmentService {

    /**
     * 提交发货信息。
     *
     * @param order 订单信息
     * @param deliveryList 物流信息列表
     * @throws AlipayApiException 支付宝接口异常
     */
    void send(Order order, List<FulfillmentDeliveryInfo> deliveryList) throws AlipayApiException;

    /**
     * 提交收货确认。
     *
     * @param order 订单信息
     * @param status 收货动作类型
     * @throws AlipayApiException 支付宝接口异常
     */
    void receive(Order order, String status) throws AlipayApiException;

    /**
     * 提交订单完结。
     *
     * @param order 订单信息
     * @param status 完结状态
     * @throws AlipayApiException 支付宝接口异常
     */
    void finish(Order order, String status) throws AlipayApiException;
}

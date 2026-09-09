package com.fly.rent.legacy.service;

import com.fly.rent.entity.Order;
import com.fly.rent.legacy.dto.apilyRentUtil.RentOrderCreateParam;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.RentOrderVto;
import com.alipay.api.AlipayApiException;
import com.alipay.api.response.AlipayCommerceRentOrderQueryResponse;

/**
 * 
 * 负责租赁订单创建、审核、退款、关单等主链路编排。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public interface RentOrderService {

    /**
     * 创建本地订单并同步创建支付宝租赁订单。
     *
     * @param rentOrderCreateParam 创建参数
     * @param userUuid 当前用户 UUID
     * @return 创建结果
     * @throws AlipayApiException 支付宝接口异常
     */
    RentOrderVto createRentOrder(RentOrderCreateParam rentOrderCreateParam, String userUuid) throws AlipayApiException;

    /**
     * 基于已存在的租赁主单创建续租子单。
     * 这里仍然走支付宝租赁组件的同一个建单本体，只是订单类型切换为 RELET，
     * 并把原单号信息一并传给支付宝，确保续租链路归属于当前已经接入的租赁订单体系。
     *
     * @param originOrder    原租赁订单，本地已落库且已关联支付宝租赁订单号
     * @param renewDuration  本次续租时长，当前按“天”处理
     * @param sourceId       续租追踪 ID；优先使用本次前端重新获取的 sourceId，缺失时回退原单 sourceId
     * @param userUuid       当前用户 UUID
     * @return 续租建单结果
     * @throws AlipayApiException 调用支付宝接口异常
     */
    RentOrderVto createReletOrder(Order originOrder, Integer renewDuration, String sourceId, String userUuid)
            throws AlipayApiException;

    /**
     * 仅创建支付宝侧租赁订单。
     *
     * @param order 本地订单
     * @return 创建结果
     * @throws AlipayApiException 支付宝接口异常
     */
    RentOrderVto createAlipayRentOrder(Order order) throws AlipayApiException;

    /**
     * 查询租赁订单状态。
     *
     * @param order 订单信息
     * @return 支付宝查询结果
     * @throws AlipayApiException 支付宝接口异常
     */
    AlipayCommerceRentOrderQueryResponse orderStatusSearch(Order order) throws AlipayApiException;

    /**
     * 商家确认审核结果。
     *
     * @param order 订单信息
     * @param isAgree 是否通过
     * @throws AlipayApiException 支付宝接口异常
     */
    void merchantConfirm(Order order, Boolean isAgree) throws AlipayApiException;

    /**
     * 提交取消并退款申请。
     *
     * @param order 订单信息
     */
    void cancelAndRefund(Order order);

    /**
     * 处理退款。
     *
     * @param order 订单信息
     * @param isAgree 是否同意退款
     * @throws AlipayApiException 支付宝接口异常
     */
    void refund(Order order, Boolean isAgree) throws AlipayApiException;

    /**
     * 主动关闭订单。
     *
     * @param order 订单信息
     * @throws AlipayApiException 支付宝接口异常
     */
    void closeOrder(Order order) throws AlipayApiException;
}

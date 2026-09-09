package com.fly.rent.legacy.service;

import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentDepositDeductRecord;
import com.alipay.api.AlipayApiException;

/**
 * 租赁订单支付服务。
 * 负责支付发起、支付结果同步和主动补偿同步。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public interface RentOrderPayService {

    /**
     * 发起订单支付。
     *
     * @param order 订单信息
     * @return 支付交易号
     * @throws AlipayApiException 支付宝接口异常
     */
    String pay(Order order) throws AlipayApiException;

    /**
     * 发起售后赔付/违约金的押金转支付。
     *
     * 私域赔付场景里，售后 create/confirm 只负责售后流程，
     * 真正的资金扣转需要额外调用租赁订单支付接口。
     *
     * @param order 订单信息
     * @param deductRecord 扣减记录，提供售后单号、外部支付单号、费用类型和金额
     * @return 支付交易号
     * @throws AlipayApiException 支付宝接口异常
     */
    String payCompensation(Order order, RentDepositDeductRecord deductRecord) throws AlipayApiException;

    /**
     * 同步支付结果并刷新本地状态。
     *
     * @param order 订单信息
     * @throws AlipayApiException 支付宝接口异常
     */
    void syncPayResult(Order order) throws AlipayApiException;

    /**
     * 主动调用支付同步接口。
     *
     * @param order 订单信息
     * @throws AlipayApiException 支付宝接口异常
     */
    void syncPay(Order order) throws AlipayApiException;
}

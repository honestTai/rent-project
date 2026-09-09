package com.fly.rent.legacy.service;

import com.fly.rent.entity.Order;
import com.fly.rent.legacy.dto.apilyRentUtil.vto.ApiResponse;

import java.util.List;

/**
 * 租赁订单预检查服务。
 * 负责对外输出下单前校验数据，以及租赁行业风险咨询能力。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public interface RentOrderCheckService {

    /**
     * 构建下单前展示给支付宝租赁侧的预检查结果。
     *
     * @param outSkuId 商品规格 ID
     * @return 预检查返回体
     */
    ApiResponse check(String outSkuId);

    /**
     * 构建指定分期期数的下单前展示数据。
     *
     * @param outSkuId 商品规格 ID
     * @param installmentCount 用户选择的期数，1 表示不分期
     * @return 预检查返回体
     */
    ApiResponse check(String outSkuId, Integer installmentCount);

    /**
     * 构建指定租期与分期期数的下单前展示数据。
     *
     * @param outSkuId 商品规格 ID
     * @param installmentCount 用户选择的期数，1 表示不分期
     * @param duration 租期天数，空值或非正数按 1 天处理
     * @return 预检查返回体
     */
    ApiResponse check(String outSkuId, Integer installmentCount, Integer duration);

    /**
     * 查询用户在支付宝租赁行业交易场景下的风险信息。
     *
     * @param order 订单信息
     * @return 风险咨询结果
     */
    Object queryUserRisk(Order order);

    /**
     * 按指定风险类型查询支付宝租赁风控信息。
     *
     * @param order 订单信息
     * @param consultRiskTypes 支付宝 consult_risk_types；空列表表示不传该字段
     * @return 风险咨询结果
     */
    Object queryUserRisk(Order order, List<String> consultRiskTypes);

}

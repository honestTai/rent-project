package com.fly.rent.legacy.dto.apilyRentUtil.vto;

/**
 * 费用信息
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public class CostInfo {
    /**
     * 订单维度原始总租金
     */
    public String originalPrice;
    /**
     * 订单维度商家侧外部优惠，暂时给0
     */
    public String discountedPrice;
    /**
     * 商家侧优惠后订单总租金
     */
    public String totalRent;
    /**
     * 押金
     */
    public String deposit;
    /**
     * 分期支付方案，支持1-24期。
     */
    public StagePayPlan stagePayPlan;
}


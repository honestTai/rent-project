package com.fly.rent.legacy.dto.apilyRentUtil.vto;


/**
 * 分期支付计划详情
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public class StagePayPlanInfo {
    /**
     * 期数
     */
    public int planPayNo;
    /**
     * 计划支付时间
     */
    public String planPayTime;
    /**
     * 原始价格
     */
    public String originalPrice;
    /**
     * 优惠价格
     */
    public String discountedPrice;
    /**
     * 计划支付价格
     */
    public String planPayPrice;
}


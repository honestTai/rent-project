package com.fly.rent.legacy.dto.apilyRentUtil;

import lombok.Data;

/**
 * 订单租赁参数
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
public class OrderRentParams {
    /**
     * 租赁时长
     */
    private int duration;
    
    /**
     * 数量
     */
    private int quantity;
    /**
     * 租赁开始时间
     */
    private String rentStartTime;
    /**
     * 租赁结束时间
     */
    private String rentEndTime;

    /**
     * 用户选择的分期期数，1 表示不分期，2-24 表示分期。
     */
    private Integer installmentCount;

    /**
     * 兼容前端可能传 periodTotal 的命名。
     */
    private Integer periodTotal;

    /**
     * 用户选择的取货方式：express 快递，store 线下自提。
     */
    private String pickupType;

    /**
     * 收获地址
     */
    private AddressInfo addressInfo;
}

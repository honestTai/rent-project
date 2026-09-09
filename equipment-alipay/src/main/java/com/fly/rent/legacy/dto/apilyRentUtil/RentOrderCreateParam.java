package com.fly.rent.legacy.dto.apilyRentUtil;

import com.fly.rent.legacy.dto.apilyRentUtil.vto.ApiResponse;
import lombok.Data;

/**
 * 租赁订单创建参数
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
public class RentOrderCreateParam {
    /**
     * 来源追踪id
     */
    private String sourceId;

    /**
     * 订单前置创建数据
     */
    private ApiResponse apiResponse;

    /**
     * 商家侧的skuId
     */
    private String outSkuId;

    /**
     * 订单租赁参数
     */
    private OrderRentParams orderRentParams;

    /**
     * 用户选择的分期期数，1 表示不分期，2-24 表示分期。
     */
    private Integer installmentCount;

    /**
     * 兼容交易页按 periodTotal 命名提交。
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


package com.fly.rent.legacy.dto.apilyRentUtil.vto;

import lombok.Data;

/**
 * 订单创建返回
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
public class RentOrderVto {
    /**
     * 成功标志，true表示成功
     */
    private boolean success;

    /**
     * 支付宝侧交易订单号
     */
    private String orderId;

    /**
     * 商家侧交易订单号
     */
    private String outOrderId;

    /**
     * 商家订单详情页链接，格式：pages/index/index?${如果有query请带上}
     */
    private String path;

    /** 
     * 错误代码
     */
    private String errorCode;

    /**
     * 错误信息
     */
    private String errorMsg;
}


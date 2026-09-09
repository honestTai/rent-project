package com.fly.rent.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单地址快照。
 * 旧表没有结构化地址字段，所以单独保存一份快照，避免后续地址被用户资料覆盖。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
public class RentAddressSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 城市
     */
    private String city;
    /**
     * 区域
     */
    private String district;
    /**
     * 详细地址
     */
    private String detail;
    /**
     * 收货人
     */
    private String consignee;
    /**
     * 手机号
     */
    private String mobile;
}

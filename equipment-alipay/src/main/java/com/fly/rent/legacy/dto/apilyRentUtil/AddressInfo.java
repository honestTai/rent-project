package com.fly.rent.legacy.dto.apilyRentUtil;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * 收获地址信息
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
public class AddressInfo {
    /**
     * 详细收货地址
     * 例如: "四川省成都市双流区..."
     */
    private String address;

    /**
     * 区/县名
     * 例如: "双流区"
     */
    private String area;

    /**
     * 城市名
     * 例如: "成都市"
     */
    private String city;

    /**
     * 城市编码
     * 例如: "510116"
     */
    private String cityCode;

    /**
     * 国家名
     * 可能为null
     */
    private String country;

    /**
     * 收货人全名
     * 例如: "周泰"
     */
    @JsonAlias({"consignee", "receiverName", "name"})
    private String fullname;

    /**
     * 是否隐藏地址
     * 例如: true
     */
    private Boolean hideAddress;

    /**
     * 收货人手机号
     * 由用户授权获取，不在源码中保留真实号码。
     */
    @JsonAlias({"mobile", "telNumber", "phoneNumber", "userTel"})
    private String mobilePhone;

    /**
     * 省份
     * 例如: "四川省"
     */
    private String prov;
}


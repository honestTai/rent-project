package com.fly.rent.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户扩展资料。
 * 和用户基础表解耦，避免因为小程序专属字段反复改动旧用户表结构。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
public class RentUserProfileExtra implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否已认证
     */
    private Boolean verified = Boolean.FALSE;
    /**
     * 风险等级
     */
    private String riskLevel = "低风险";
    /**
     * 信用分
     */
    private Integer creditScore = 650;
    /**
     * 城市
     */
    private String city;
    /**
     * 身份证后四位
     */
    private String idCardTail = "";
    /**
     * 真实姓名
     */
    private String realName;
    /**
     * 身份证号
     */
    private String idCard;
    /**
     * 上次认证时间
     */
    private Long lastVerifyAt;
}

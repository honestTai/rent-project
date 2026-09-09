package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 用户表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends Model<User> {

    private static final long serialVersionUID = 1L;

    @TableId(value = "user_id", type = IdType.AUTO)
    private Integer userId;

    @TableField("nickname")
    private String userTitle;

    @TableField("avatar")
    private String userAvatar;

    private String openid;

    private String uuid;

    @TableField("alipay_user_id")
    private String alipayUserId;

    @TableField("balance")
    private Integer userBalance;

    @TableField("total_balance")
    private Integer userTotalBalance;

    @TableField("address")
    private String addr;

    @TableField("phone")
    private String userTel;

    @TableField("birthday")
    private Long userBirth;

    @TableField("account")
    private String uAcco;

    @TableField("password")
    private String uPass;

    @TableField("share_cover")
    private String userShpic;

    @TableField("share_text")
    private String userShtxt;

    @TableField("qrcode")
    private String userQrcode;

    @TableField("created_at")
    private Long createtime;

    @TableField("dist_created_at")
    private Long distcretime;

    @TableField("parent_user_id")
    private Integer userUpLevel1;

    @TableField("grandparent_user_id")
    private Integer userUpLevel2;

    @TableField("merchant_phone")
    private String merchantTel;

    @TableField("pickup_location")
    private String location;

    @TableField("return_location")
    private String locationReturn;

    @TableField("return_contact_name")
    private String returnName;

    @TableField("return_contact_phone")
    private String returnTel;

    @TableField("is_blocked")
    private Integer isNoRequest;

    /** 真实姓名（实名认证） */
    @TableField("real_name")
    private String realName;

    /** 身份证号（实名认证） */
    @TableField("id_card")
    private String idCard;

    @Override
    public Serializable pkVal() {
        return this.userId;
    }
}

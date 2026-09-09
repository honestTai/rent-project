package com.equipment.platform.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 中台用户实体。
 * <p>
 * 字段保持兼容原租赁后台 user 表和前端用户管理页面，便于用户表迁入中台库后不改前端字段。
 * </p>
 */
@Data
@TableName("`user`")
public class PlatformUser {

    /** 用户主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 用户真实姓名。 */
    @TableField("realName")
    private String realName;

    /** 登录账号。 */
    @TableField("userName")
    private String userName;

    /** 登录密码密文。 */
    @TableField("userPwd")
    private String userPwd;

    /** 用户手机号。 */
    @TableField("userPhoneNum")
    private String userPhoneNum;

    /** 用户性别，0 表示男，1 表示女。 */
    @TableField("userSex")
    private Integer userSex;

    /** 用户创建时间。 */
    @TableField("creatUserDate")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date creatUserDate;

    /** 小程序用户 UUID，后台账号默认 -1。 */
    @TableField("uuid")
    private String uuid = "-1";

    /** 是否禁止请求，沿用历史字段语义。 */
    @TableField("isNoRequest")
    private Integer isNoRequest = 0;

    /** 前端用户管理提交的角色授权集合，不落 user 表。 */
    @TableField(exist = false)
    private List<Long> roleIds;
}

package com.common.login;

import com.common.Util.UserInfo.UserInfo;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 登录用户会话信息，网关登录成功后会把基础用户信息和 RBAC 权限快照一起写入 Redis。
 */
@Data
public class LoginUser implements UserInfo {

    private Integer id;

    private String realName;

    private String userName;

    private String userPwd;

    private String userPhoneNum;

    private Integer userSex;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date creatUserDate;

    private String uuid = "-1";

    private Integer isNoRequest = 0;

    /**
     * 当前用户拥有的中台角色编码。
     */
    private List<String> roleCodes = new ArrayList<>();

    /**
     * 当前用户拥有的页面权限编码，格式为 systemCode:pageCode。
     */
    private List<String> pageCodes = new ArrayList<>();

    /**
     * 当前用户拥有的按钮权限编码，格式为 systemCode:pageCode:buttonCode。
     */
    private List<String> buttonCodes = new ArrayList<>();
}

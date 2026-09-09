package com.common.login;

import lombok.Data;

/**
 * 登录参数（公共DTO，两个业务模块共享）
 */
@Data
public class LoginParam {
    /**
     * 登录用户名
     */
    private String userName;

    /**
     * 登录密码
     */
    private String userPwd;

    public LoginParam(String userName, String userPwd) {
        this.userName = userName;
        this.userPwd = userPwd;
    }

    public LoginParam() {
    }
}

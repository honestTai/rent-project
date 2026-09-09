package com.common.login;

import com.common.Entity.ReturnResult;

import javax.servlet.http.HttpServletRequest;

/**
 * 登录接口（公共接口，两个业务模块共享）
 */
public interface LoginService {

    /**
     * 用户登录
     */
    ReturnResult login(LoginParam loginParam, HttpServletRequest httpServletRequest);
}

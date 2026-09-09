package com.common.Util.UserInfo;

/**
 * 用户信息接口，用于解耦 AOP 切面对具体 User 实体的依赖。
 * 各业务模块的 User 实体应实现此接口。
 */
public interface UserInfo {

    /**
     * 获取用户ID
     */
    Integer getId();

    /**
     * 获取用户名
     */
    String getUserName();

    /**
     * 获取用户密码
     */
    String getUserPwd();

    String getUuid();
}

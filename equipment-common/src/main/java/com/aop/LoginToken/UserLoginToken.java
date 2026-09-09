package com.aop.LoginToken;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用户登录校验注解
 * 标记在 Controller 方法或类上，用于指示该接口需要进行用户登录验证。
 * 配合 InterceptorConfig 拦截器使用。
 * 
 * 使用方式：
 * @UserLoginToken(required = true)  // 需要登录（默认）
 * @UserLoginToken(required = false) // 可选登录
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UserLoginToken {
    /**
     * 是否必须登录
     * true: 未登录请求将被拦截并返回 401
     * false: 允许未登录访问，但如果携带 Token 仍会解析用户信息
     */
    boolean required() default true;
}

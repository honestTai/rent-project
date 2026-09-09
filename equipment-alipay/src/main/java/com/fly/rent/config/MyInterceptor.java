package com.fly.rent.config;

import com.fly.rent.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 小程序 JWT 登录态拦截器。
 * 仅对 /api/rent/v1/miniapp/** 生效（在 MyWebAppConfigurer 中注册），校验 token 及用户是否被禁止访问。
 * 其余接口鉴权由网关处理，不经过本拦截器。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Component
@Slf4j
public class MyInterceptor implements HandlerInterceptor {

    @Autowired
    private RedisUserInfo redisUserInfo;

    @Autowired
    private RequestTokenResolver requestTokenResolver;

    @Autowired
    private JwtHelper jwtHelper;

    /**
     * 预处理回调方法，实现处理器的预处理
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @return true表示继续流程，false表示流程中断
     * @throws Exception 异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = requestTokenResolver.resolve(request);

        if (request.getRequestURI().contains("notify")) {
            return true;
        }
        if (token == null) {
            throw new TokenException("无 token，请登录");
        }

        User user = redisUserInfo.userInfo();
        if (user == null) {
            throw new TokenException("无 token，请登录");
        }
        if (!"-1".equals(user.getUuid()) && user.getIsNoRequest().equals(1)) {
            log.info("禁止访问用户 uuid: {}", user.getUuid());
            throw new NoUseException("禁止使用本系统");
        }

        boolean verified = jwtHelper.verify(token, user.getUAcco(), user.getUPass());
        if (!verified) {
            throw new TokenException("无 token，请登录");
        }
        return true;
    }

    /**
     * 后处理回调方法，实现处理器的后处理（但在渲染视图之前）
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @param modelAndView 模型和视图
     */
    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            @Nullable ModelAndView modelAndView
    ) {
    }

    /**
     * 整个请求处理完毕回调方法
     * @param request 请求
     * @param response 响应
     * @param handler 处理器
     * @param ex 异常
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            @Nullable Exception ex
    ) {
    }
}

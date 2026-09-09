package com.fly.rent.config;

import com.common.Util.UserInfo.UserContextHeaders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Web / Admin 接口网关鉴权拦截器。
 * 确保 /api/web/** 等请求必须经过网关认证（携带网关转发的用户上下文头），
 * 防止小程序 token 或直连服务绕过网关访问管理接口。
 */
@Component
@Slf4j
public class GatewayAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String gatewayUserName = request.getHeader(UserContextHeaders.USER_NAME);
        String gatewayUserUuid = request.getHeader(UserContextHeaders.USER_UUID);

        if (!StringUtils.hasText(gatewayUserName) || !StringUtils.hasText(gatewayUserUuid)) {
            log.warn("拒绝未经网关认证的管理接口请求: {} {}", request.getMethod(), request.getRequestURI());
            throw new TokenException("无权限，请通过管理后台登录");
        }
        return true;
    }
}

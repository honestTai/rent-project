package com.fly.rent.config;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * 统一解析请求中的登录令牌。
 * 新接口优先走 Authorization: Bearer，历史 header token 作为兜底。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Component
public class RequestTokenResolver {

    /**
     * Bearer前缀
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 解析Token
     * @param request 请求
     * @return Token字符串
     */
    public String resolve(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return request.getHeader("token");
    }
}

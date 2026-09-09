package com.equipment.gateway.controller;

import com.common.Entity.ReturnResult;
import com.common.login.LoginParam;
import com.common.login.LoginSuccess;
import com.equipment.gateway.service.GatewayLoginService;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class GatewayLoginController {

    private final GatewayLoginService gatewayLoginService;

    public GatewayLoginController(GatewayLoginService gatewayLoginService) {
        this.gatewayLoginService = gatewayLoginService;
    }

    /** 请求头：标识登录所属系统；后台账号密码登录只允许中台发起。 */
    public static final String HEADER_LOGIN_SYSTEM = "X-Login-System";
    private static final String PLATFORM_LOGIN_SYSTEM = "platform";

    @PostMapping("/api/login/login")
    public Mono<ReturnResult<LoginSuccess>> login(@RequestBody LoginParam loginParam,
                                                  ServerHttpRequest request) {
        String clientIp = resolveClientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String loginSystem = request.getHeaders().getFirst(HEADER_LOGIN_SYSTEM);
        if (!StringUtils.hasText(loginSystem) || !PLATFORM_LOGIN_SYSTEM.equalsIgnoreCase(loginSystem.trim())) {
            return Mono.just(new ReturnResult<>(403, "后台登录已统一到中台", null));
        }
        return gatewayLoginService.login(loginParam, clientIp, userAgent, loginSystem);
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}

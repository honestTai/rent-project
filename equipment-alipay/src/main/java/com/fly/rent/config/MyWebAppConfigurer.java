package com.fly.rent.config;

import org.apache.http.HttpHeaders;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * Web 基础设施配置。
 * - /api/web/**（含统一上传 /api/web/oss/**）：由 GatewayAuthInterceptor 验证网关转发的用户头。
 * - /api/rent/v1/miniapp/**：由 MyInterceptor 做小程序 JWT 鉴权。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Configuration
public class MyWebAppConfigurer implements WebMvcConfigurer {

    @Resource
    private MyInterceptor myInterceptor;

    @Resource
    private GatewayAuthInterceptor gatewayAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 后台管理接口：/api/web/**（含 /api/web/oss/upload），须经网关认证
        registry.addInterceptor(gatewayAuthInterceptor)
                .addPathPatterns("/api/web/**");

        // 2. 小程序接口：由本应用 JWT 鉴权，认证/回调/公开目录除外
        List<String> miniappExcludePaths = new ArrayList<>();
        miniappExcludePaths.add("/api/rent/v1/miniapp/auth/**");
        miniappExcludePaths.add("/api/rent/v1/miniapp/catalog/banners/query");
        miniappExcludePaths.add("/api/rent/v1/miniapp/catalog/goods/query");
        miniappExcludePaths.add("/api/rent/v1/miniapp/catalog/categories/query");
        miniappExcludePaths.add("/api/rent/v1/miniapp/catalog/config");
        miniappExcludePaths.add("/api/rent/v1/miniapp/callbacks/**");

        registry.addInterceptor(myInterceptor)
                .addPathPatterns("/api/rent/v1/miniapp/**")
                .excludePathPatterns(miniappExcludePaths);
    }

    /**
     * 全局 CORS 过滤器，允许所有来源、方法和请求头，支持携带凭证。
     *
     * @return CORS 过滤器
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*"); // 使用 addAllowedOriginPattern 替代 addAllowedOrigin("*")
        config.setAllowCredentials(true);
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.addExposedHeader(HttpHeaders.ACCEPT);

        UrlBasedCorsConfigurationSource configSource = new UrlBasedCorsConfigurationSource();
        configSource.registerCorsConfiguration("/**", config);
        return new CorsFilter(configSource);
    }
}

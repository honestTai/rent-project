package com.aop.LoginToken;

import com.aop.Role.RoleTokenAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(name = "equipment.security.local-auth-enabled", havingValue = "true")
public class InterceptorConfig implements WebMvcConfigurer {
    /**
     * 登录拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userLoginInterceptor())
                // 拦截所有请求，通过判断@UserLoginTokenAspect 决定是否需要登录
                .addPathPatterns("/**");
        registry.addInterceptor(roleTokenAspect()).addPathPatterns("/**");
    }

    /**
     * 是否登录拦截器
     * @return
     */
    @Bean
    public UserLoginTokenAspect userLoginInterceptor() {
        return new UserLoginTokenAspect();
    }



    @Bean
    public RoleTokenAspect roleTokenAspect() {
        return new RoleTokenAspect();
    }
}

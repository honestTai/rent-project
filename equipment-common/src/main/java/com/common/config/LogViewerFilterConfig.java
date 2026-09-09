package com.common.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;

@Configuration
public class LogViewerFilterConfig {

    @Bean
    public FilterRegistrationBean<Filter> logViewerAuthFilterRegistration(
            LogViewerAuthFilter filter, LogViewerProperties properties) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns(properties.getUrlMapping());
        registration.setName("logViewerAuthFilter");
        registration.setOrder(1);
        return registration;
    }
}

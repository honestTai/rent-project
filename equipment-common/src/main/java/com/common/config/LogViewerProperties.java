package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "log-viewer")
public class LogViewerProperties {

    /**
     * Servlet 映射路径，如 /logs/*
     */
    private String urlMapping = "/logs/*";

    /**
     * IP 白名单，支持精确 IP 和 CIDR 网段（如 192.168.0.0/16）
     * 留空或不配置 = 不限制 IP
     */
    private List<String> allowedIps = new ArrayList<>();

    /**
     * 同一 IP 最大登录失败次数，超过后锁定
     */
    private int maxLoginAttempts = 5;

    /**
     * 登录失败后锁定时长（分钟）
     */
    private int lockDurationMinutes = 30;

    /**
     * 登录 Session 有效期（分钟）
     */
    private int sessionTimeout = 30;

    public String getUrlMapping() {
        return urlMapping;
    }

    public void setUrlMapping(String urlMapping) {
        this.urlMapping = urlMapping;
    }

    /**
     * 从 urlMapping 提取基础路径，如 /logs/* -> /logs
     */
    public String getBasePath() {
        String path = urlMapping;
        if (path.endsWith("/*")) {
            path = path.substring(0, path.length() - 2);
        } else if (path.endsWith("*")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    public List<String> getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(List<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    public int getMaxLoginAttempts() {
        return maxLoginAttempts;
    }

    public void setMaxLoginAttempts(int maxLoginAttempts) {
        this.maxLoginAttempts = maxLoginAttempts;
    }

    public int getLockDurationMinutes() {
        return lockDurationMinutes;
    }

    public void setLockDurationMinutes(int lockDurationMinutes) {
        this.lockDurationMinutes = lockDurationMinutes;
    }

    public int getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }
}

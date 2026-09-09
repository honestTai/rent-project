package com.equipment.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "equipment.gateway.auth")
public class GatewayAuthProperties {

    private List<String> excludePaths = new ArrayList<>();

    private List<String> adminPaths = new ArrayList<>();

    private List<String> uuidAdminPaths = new ArrayList<>();

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public List<String> getAdminPaths() {
        return adminPaths;
    }

    public void setAdminPaths(List<String> adminPaths) {
        this.adminPaths = adminPaths;
    }

    public List<String> getUuidAdminPaths() {
        return uuidAdminPaths;
    }

    public void setUuidAdminPaths(List<String> uuidAdminPaths) {
        this.uuidAdminPaths = uuidAdminPaths;
    }
}

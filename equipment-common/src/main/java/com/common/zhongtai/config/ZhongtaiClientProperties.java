package com.common.zhongtai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 中台服务客户端配置。
 * <p>
 * 微服务部署时，业务服务通过 HTTP 访问中台服务读取配置、通知通道和任务配置，
 * 避免业务库拆分后继续跨库查询中台表。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "zhongtai.client")
public class ZhongtaiClientProperties {

    /** 是否启用中台 HTTP 客户端。 */
    private boolean enabled = true;

    /** 中台服务基础地址，例如 http://127.0.0.1:7780 或 http://equipment-platform-service:9804。 */
    private String baseUrl;

    /** 请求超时时间，单位毫秒。 */
    private int timeoutMillis = 3000;
}

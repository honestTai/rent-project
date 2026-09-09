package com.common.oss;

import com.common.zhongtai.config.ZhongtaiConfigService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 阿里云 OSS 配置。
 * <p>
 * OSS 运行配置统一从中台读取，不再回退各业务模块 application.yml。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    private transient ZhongtaiConfigService zhongtaiConfigService;

    /** 地域节点，如 oss-cn-hangzhou.aliyuncs.com，不要带协议 */
    private String endpoint = "";

    private String accessKeyId = "";

    private String accessKeySecret = "";

    private String bucketName = "";

    /** 上传方式：aliyun 表示阿里云 OSS，local 表示写入本地 uploads 目录。 */
    private String storageType = "aliyun";

    /** 本地上传根目录，容器部署默认映射到宿主机 customer-deploy/uploads。 */
    private String localRootPath = "/app/uploads";

    /** 阿里云 OSS 公开访问前缀，如 https://bucket.oss-cn-hangzhou.aliyuncs.com/uploads。 */
    private String aliyunPublicBaseUrl = "";

    /** 本地上传公开访问前缀，如 https://example.com/uploads。 */
    private String localPublicBaseUrl = "";

    /** 兼容旧配置，优先使用 aliyun/local 两个专用回显前缀。 */
    private String publicBaseUrl = "";

    /**
     * 注入中台配置读取服务。
     *
     * @param zhongtaiConfigService 中台配置读取服务
     */
    @Autowired
    public void setZhongtaiConfigService(ZhongtaiConfigService zhongtaiConfigService) {
        this.zhongtaiConfigService = zhongtaiConfigService;
    }

    public boolean isConfigured() {
        return getEndpoint() != null && !getEndpoint().isEmpty()
                && getBucketName() != null && !getBucketName().isEmpty()
                && getAccessKeyId() != null && !getAccessKeyId().isEmpty()
                && getAccessKeySecret() != null && !getAccessKeySecret().isEmpty();
    }

    public String getEndpoint() {
        return getConfig("oss.endpoint");
    }

    public String getAccessKeyId() {
        return getConfig("oss.access-key-id");
    }

    public String getAccessKeySecret() {
        return getConfig("oss.access-key-secret");
    }

    public String getBucketName() {
        return getConfig("oss.bucket-name");
    }

    public String getStorageType() {
        String configured = null;
        if (zhongtaiConfigService != null) {
            configured = zhongtaiConfigService.getOptionalString("common", "oss.storage-type");
        }
        String value = StringUtils.hasText(configured) ? configured : storageType;
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : "aliyun";
    }

    public String getLocalRootPath() {
        String configured = null;
        if (zhongtaiConfigService != null) {
            configured = zhongtaiConfigService.getOptionalString("common", "oss.local-root-path");
        }
        String value = StringUtils.hasText(configured) ? configured : localRootPath;
        return StringUtils.hasText(value) ? value.trim() : "/app/uploads";
    }

    public String getPublicBaseUrl() {
        if (zhongtaiConfigService == null) {
            return normalize(publicBaseUrl);
        }
        String configured = zhongtaiConfigService.getOptionalString("common", "oss.public-base-url");
        return normalize(StringUtils.hasText(configured) ? configured : publicBaseUrl);
    }

    public String getAliyunPublicBaseUrl() {
        String configured = readOptionalConfig("oss.aliyun-public-base-url");
        String normalized = normalize(StringUtils.hasText(configured) ? configured : aliyunPublicBaseUrl);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return "https://" + getBucketName() + "." + getEndpoint() + "/uploads";
    }

    public String getLocalPublicBaseUrl() {
        String configured = readOptionalConfig("oss.local-public-base-url");
        String normalized = normalize(StringUtils.hasText(configured) ? configured : localPublicBaseUrl);
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        String legacy = getPublicBaseUrl();
        return StringUtils.hasText(legacy) ? legacy : "/uploads";
    }

    public String getCurrentPublicBaseUrl() {
        return "local".equals(getStorageType()) ? getLocalPublicBaseUrl() : getAliyunPublicBaseUrl();
    }

    public String buildAliyunPublicUrl(String relativePath) {
        String relative = normalizeRelativePath(relativePath);
        return getAliyunPublicBaseUrl() + "/" + relative;
    }

    public String buildLocalPublicUrl(String relativePath) {
        String relative = normalizeRelativePath(relativePath);
        return getLocalPublicBaseUrl() + "/" + relative;
    }

    private String getConfig(String configKey) {
        return zhongtaiConfigService.getString("common", configKey);
    }

    private String readOptionalConfig(String configKey) {
        if (zhongtaiConfigService == null) {
            return null;
        }
        return zhongtaiConfigService.getOptionalString("common", configKey);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeRelativePath(String path) {
        String normalized = StringUtils.hasText(path) ? path.trim().replace("\\", "/") : "";
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("uploads/")) {
            normalized = normalized.substring("uploads/".length());
        }
        return normalized;
    }
}

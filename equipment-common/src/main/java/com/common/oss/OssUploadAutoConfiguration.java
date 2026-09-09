package com.common.oss;

import com.common.zhongtai.config.ZhongtaiConfigService;
import com.common.zhongtai.config.ZhongtaiClientProperties;
import com.common.zhongtai.config.ZhongtaiNotifyChannelService;
import com.common.zhongtai.config.ZhongtaiPlatformClient;
import com.common.zhongtai.config.ZhongtaiScheduleTaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OSS 上传自动配置：注册 {@link OssProperties} 与 {@link OssFileStorage}。
 * <p>
 * 扫描 {@code com.common} 的服务会自动加载。<br>
 * <b>equipment-alipay</b>：请在启动类增加 {@code @Import(OssUploadAutoConfiguration.class)}。
 */
@Configuration
@EnableConfigurationProperties({OssProperties.class, ZhongtaiClientProperties.class})
public class OssUploadAutoConfiguration {

    /**
     * 注册中台服务 HTTP 客户端。
     *
     * @param properties 中台客户端配置
     * @return 中台 HTTP 客户端
     */
    @Bean
    @ConditionalOnMissingBean
    public ZhongtaiPlatformClient zhongtaiPlatformClient(ZhongtaiClientProperties properties) {
        return new ZhongtaiPlatformClient(properties);
    }

    /**
     * 注册中台配置读取服务。
     * <p>
     * 部分业务服务通过 Import 使用 OSS 上传能力，不扫描 com.common.zhongtai 包，这里补充注册中台读取服务。
     * </p>
     *
     * @param platformClientProvider 中台服务 HTTP 客户端提供器
     * @return 中台配置读取服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ZhongtaiConfigService zhongtaiConfigService(ObjectProvider<ZhongtaiPlatformClient> platformClientProvider) {
        return new ZhongtaiConfigService(platformClientProvider);
    }

    /**
     * 部分业务服务只导入 OSS 自动配置时，也需要能读取中台任务配置。
     *
     * @param platformClientProvider 中台服务 HTTP 客户端提供器
     * @return 中台定时任务配置读取服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ZhongtaiScheduleTaskService zhongtaiScheduleTaskService(ObjectProvider<ZhongtaiPlatformClient> platformClientProvider) {
        return new ZhongtaiScheduleTaskService(platformClientProvider);
    }

    /**
     * 部分业务服务只导入 OSS 自动配置时，也需要能读取中台通知通道。
     *
     * @param platformClientProvider 中台服务 HTTP 客户端提供器
     * @return 中台通知通道读取服务
     */
    @Bean
    @ConditionalOnMissingBean
    public ZhongtaiNotifyChannelService zhongtaiNotifyChannelService(ObjectProvider<ZhongtaiPlatformClient> platformClientProvider) {
        return new ZhongtaiNotifyChannelService(platformClientProvider);
    }

    /**
     * 注册 OSS 文件存储实现。
     * <p>
     * 服务侧如果已经自定义 {@link OssFileStorage}，这里不再覆盖，避免不同业务服务接入自有存储实现时冲突。
     * </p>
     *
     * @param ossProperties OSS 运行配置
     * @return OSS 文件存储实现
     */
    @Bean
    @ConditionalOnMissingBean(OssFileStorage.class)
    public OssFileStorage ossFileStorage(OssProperties ossProperties) {
        return new ConfigurableOssFileStorage(ossProperties);
    }

    /**
     * 注册统一 OSS 上传控制器。
     * <p>
     * platform、alipay 这类扫描 {@code com.common} 的服务会自动扫描到
     * {@link OssUploadController}；alipay 这类只 {@code @Import} 自动配置的服务才需要这里补注册。
     * 因此必须按类型和 Bean 名同时防重，避免同一个控制器注册两次导致应用启动失败。
     * </p>
     *
     * @param ossFileStorage OSS 文件存储实现
     * @return OSS 上传控制器
     */
    @Bean
    @ConditionalOnMissingBean(value = OssUploadController.class, name = "ossUploadController")
    public OssUploadController ossUploadController(OssFileStorage ossFileStorage) {
        return new OssUploadController(ossFileStorage);
    }
}

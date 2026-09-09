package com.common.notify.feishu;

import com.common.Util.Redis.RedisClient;
import com.common.zhongtai.config.ZhongtaiClientProperties;
import com.common.zhongtai.config.ZhongtaiConfigService;
import com.common.zhongtai.config.ZhongtaiNotifyChannelService;
import com.common.zhongtai.config.ZhongtaiPlatformClient;
import com.common.zhongtai.config.ZhongtaiScheduleTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 飞书通知自动配置。
 * 统一注册配置、token 管理、消息客户端和高层通知门面，供各业务模块按需注入。
 */
@Configuration
@EnableConfigurationProperties({FeishuNotifyProperties.class, ZhongtaiClientProperties.class})
public class FeishuNotifyAutoConfiguration {

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
     * 支付宝租赁服务通过显式 Import 加载飞书配置，不扫描 com.common 包，因此这里补充注册读取服务。
     * 已经扫描 com.common 的业务模块会命中 ConditionalOnMissingBean，避免重复注册。
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
     * 注册中台定时任务配置读取服务。
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
     * 注册中台通知通道读取服务。
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
     * 飞书通知独立线程池。
     * 与主业务线程解耦，避免通知网络抖动或飞书接口超时占用下单、回调、报表生成等核心流程。
     */
    @Bean("feishuNotifyExecutor")
    @ConditionalOnMissingBean(name = "feishuNotifyExecutor")
    public Executor feishuNotifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("feishu-notify-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuTenantAccessTokenService feishuTenantAccessTokenService(FeishuNotifyProperties properties,
                                                                         ObjectProvider<RedisClient> redisClientProvider,
                                                                         ObjectMapper objectMapper) {
        // 支付宝租赁后台当前未显式注入 RedisClient，通知能力需要允许无缓存启动，
        // 在这种情况下退化为每次按需申请 token，但不影响主业务与服务启动。
        return new FeishuTenantAccessTokenService(properties, redisClientProvider.getIfAvailable(), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuMessageClient feishuMessageClient(FeishuNotifyProperties properties,
                                                   FeishuTenantAccessTokenService accessTokenService,
                                                   ObjectMapper objectMapper) {
        return new FeishuMessageClient(properties, accessTokenService, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuCardBuilder feishuCardBuilder(ObjectMapper objectMapper) {
        return new FeishuCardBuilder(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuNotifyService feishuNotifyService(FeishuNotifyProperties properties,
                                                   FeishuCardBuilder cardBuilder,
                                                   FeishuMessageClient messageClient,
                                                   ZhongtaiNotifyChannelService zhongtaiNotifyChannelService,
                                                   @Qualifier("feishuNotifyExecutor") Executor feishuNotifyExecutor) {
        return new FeishuNotifyService(properties, cardBuilder, messageClient, feishuNotifyExecutor, zhongtaiNotifyChannelService);
    }
}

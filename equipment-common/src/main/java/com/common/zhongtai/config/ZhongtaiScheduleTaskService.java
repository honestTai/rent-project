package com.common.zhongtai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.support.CronSequenceGenerator;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中台定时任务配置读取服务。
 * <p>
 * 业务服务仍然保留具体任务执行逻辑，但触发时间和启停状态从 {@code platform_schedule_task} 读取。
 * 这样中台调整 cron 或关闭任务后，业务服务无需重新发版。
 * </p>
 */
@Slf4j
public class ZhongtaiScheduleTaskService {

    private final ObjectProvider<ZhongtaiPlatformClient> platformClientProvider;
    private final Map<String, Date> lastCheckTimes = new ConcurrentHashMap<>();

    /**
     * 创建中台定时任务配置读取服务。
     *
     * @param platformClientProvider 中台服务 HTTP 客户端提供器
     */
    public ZhongtaiScheduleTaskService(ObjectProvider<ZhongtaiPlatformClient> platformClientProvider) {
        this.platformClientProvider = platformClientProvider;
    }

    /**
     * 判断当前任务本次轮询是否应该执行。
     *
     * @param systemCode 系统编码
     * @param taskCode 任务编码
     * @return true 表示当前轮询应执行任务
     */
    public boolean shouldRunNow(String systemCode, String taskCode) {
        String key = buildKey(systemCode, taskCode);
        TaskConfig config = readTaskConfig(systemCode, taskCode);
        if (!config.isEnabled()) {
            lastCheckTimes.remove(key);
            return false;
        }
        String cron = config.getCronExpr();
        if (!StringUtils.hasText(cron)) {
            log.warn("中台定时任务 cron 为空，跳过本次执行: systemCode={}, taskCode={}", systemCode, taskCode);
            lastCheckTimes.remove(key);
            return false;
        }
        Date now = new Date();
        Date lastCheck = lastCheckTimes.get(key);
        if (lastCheck == null) {
            lastCheckTimes.put(key, now);
            return false;
        }
        try {
            CronSequenceGenerator generator = new CronSequenceGenerator(cron);
            Date next = generator.next(lastCheck);
            if (!now.before(next)) {
                lastCheckTimes.put(key, now);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("中台定时任务 cron 不合法，跳过本次执行: systemCode={}, taskCode={}, cron={}", systemCode, taskCode, cron);
            lastCheckTimes.put(key, now);
            return false;
        }
    }

    /**
     * 判断任务是否启用。
     *
     * @param systemCode 系统编码
     * @param taskCode 任务编码
     * @return 是否启用
     */
    public boolean isEnabled(String systemCode, String taskCode) {
        return readTaskConfig(systemCode, taskCode).isEnabled();
    }

    private TaskConfig readTaskConfig(String systemCode, String taskCode) {
        try {
            return readFromPlatform(systemCode, taskCode);
        } catch (Exception e) {
            log.warn("从中台服务读取任务配置失败，跳过本次执行: systemCode={}, taskCode={}, message={}",
                    systemCode, taskCode, e.getMessage());
            return TaskConfig.disabled();
        }
    }

    /**
     * 从中台服务读取任务配置。
     */
    private TaskConfig readFromPlatform(String systemCode, String taskCode) {
        ZhongtaiPlatformClient client = platformClientProvider == null ? null : platformClientProvider.getIfAvailable();
        if (client == null || !client.isAvailable()) {
            throw new IllegalStateException("未配置中台服务地址: zhongtai.client.base-url");
        }
        try {
            ZhongtaiScheduleTaskConfig config = client.getScheduleTask(systemCode, taskCode);
            if (config == null) {
                throw new IllegalStateException("中台定时任务配置缺失: systemCode=" + systemCode + ", taskCode=" + taskCode);
            }
            return new TaskConfig(config.getCronExpr(), config.isEnabled());
        } catch (Exception e) {
            throw new IllegalStateException("从中台服务读取任务配置失败: systemCode=" + systemCode + ", taskCode=" + taskCode, e);
        }
    }

    private String buildKey(String systemCode, String taskCode) {
        return (systemCode == null ? "" : systemCode.trim()) + ":" + (taskCode == null ? "" : taskCode.trim());
    }

    /**
     * 中台任务配置。
     */
    private static class TaskConfig {
        private final String cronExpr;
        private final boolean enabled;

        private TaskConfig(String cronExpr, boolean enabled) {
            this.cronExpr = cronExpr;
            this.enabled = enabled;
        }

        private static TaskConfig disabled() {
            return new TaskConfig(null, false);
        }

        private String getCronExpr() {
            return cronExpr;
        }

        private boolean isEnabled() {
            return enabled;
        }
    }
}

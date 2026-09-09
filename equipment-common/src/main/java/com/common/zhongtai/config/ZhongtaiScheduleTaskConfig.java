package com.common.zhongtai.config;

import lombok.Data;

/**
 * 中台任务配置快照。
 */
@Data
public class ZhongtaiScheduleTaskConfig {

    /** cron 表达式。 */
    private String cronExpr;

    /** 是否启用。 */
    private boolean enabled;
}

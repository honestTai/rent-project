package com.equipment.platform.controller;

import com.common.Entity.ReturnResult;
import com.equipment.platform.dto.PlatformQuery;
import com.equipment.platform.entity.PlatformConfig;
import com.equipment.platform.entity.PlatformNotifyChannel;
import com.equipment.platform.entity.PlatformScheduleTask;
import com.equipment.platform.service.PlatformConfigService;
import com.equipment.platform.service.PlatformNotifyChannelService;
import com.equipment.platform.service.PlatformScheduleTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.common.Constant.constant.SUCCESS;

/**
 * 中台内部运行时接口。
 * <p>
 * 该接口供租赁、二手、支付宝等业务微服务读取配置、通知通道和任务配置。
 * 返回运行时原值，不做前端管理页的敏感字段脱敏。
 * </p>
 */
@RestController
@RequestMapping("/api/platform/internal")
public class PlatformInternalController {

    private final PlatformConfigService configService;
    private final PlatformNotifyChannelService notifyChannelService;
    private final PlatformScheduleTaskService scheduleTaskService;

    /**
     * 创建中台内部运行时接口。
     *
     * @param configService 配置服务
     * @param notifyChannelService 通知通道服务
     * @param scheduleTaskService 任务配置服务
     */
    public PlatformInternalController(PlatformConfigService configService,
                                      PlatformNotifyChannelService notifyChannelService,
                                      PlatformScheduleTaskService scheduleTaskService) {
        this.configService = configService;
        this.notifyChannelService = notifyChannelService;
        this.scheduleTaskService = scheduleTaskService;
    }

    /**
     * 读取运行时配置值。
     *
     * @param systemCode 系统编码
     * @param configKey 配置键
     * @return 配置值
     */
    @GetMapping("/config/value")
    public ReturnResult<String> configValue(@RequestParam("systemCode") String systemCode,
                                            @RequestParam("configKey") String configKey) {
        return new ReturnResult<>(SUCCESS, "查询成功", configService.getRuntimeValue(systemCode, configKey));
    }

    /**
     * 查询运行时配置列表。
     *
     * @param systemCode 系统编码
     * @param configGroup 配置分组
     * @return 配置列表
     */
    @GetMapping("/config/list")
    public ReturnResult<List<PlatformConfig>> configList(@RequestParam("systemCode") String systemCode,
                                                         @RequestParam("configGroup") String configGroup) {
        PlatformQuery query = new PlatformQuery();
        query.setSystemCode(systemCode);
        query.setGroup(configGroup);
        return new ReturnResult<>(SUCCESS, "查询成功", configService.list(query));
    }

    /**
     * 保存运行时配置。
     *
     * @param config 配置实体
     * @return 保存结果
     */
    @PostMapping("/config/save")
    public ReturnResult<Void> saveConfig(@RequestBody PlatformConfig config) {
        configService.save(config);
        return new ReturnResult<>(SUCCESS, "保存成功");
    }

    /**
     * 读取运行时通知通道。
     *
     * @param systemCode 系统编码
     * @param sceneCode 场景编码
     * @param channelCode 通道编码
     * @return 通知通道
     */
    @GetMapping("/notify-channel")
    public ReturnResult<PlatformNotifyChannel> notifyChannel(@RequestParam("systemCode") String systemCode,
                                                             @RequestParam("sceneCode") String sceneCode,
                                                             @RequestParam("channelCode") String channelCode) {
        return new ReturnResult<>(SUCCESS, "查询成功",
                notifyChannelService.getRuntimeChannel(systemCode, sceneCode, channelCode));
    }

    /**
     * 读取运行时任务配置。
     *
     * @param systemCode 系统编码
     * @param taskCode 任务编码
     * @return 任务配置
     */
    @GetMapping("/schedule-task")
    public ReturnResult<PlatformScheduleTask> scheduleTask(@RequestParam("systemCode") String systemCode,
                                                           @RequestParam("taskCode") String taskCode) {
        return new ReturnResult<>(SUCCESS, "查询成功", scheduleTaskService.getRuntimeTask(systemCode, taskCode));
    }
}

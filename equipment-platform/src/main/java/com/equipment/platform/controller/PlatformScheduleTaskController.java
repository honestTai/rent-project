package com.equipment.platform.controller;

import com.common.Entity.ReturnResult;
import com.equipment.platform.dto.PlatformQuery;
import com.equipment.platform.entity.PlatformScheduleTask;
import com.equipment.platform.service.PlatformScheduleTaskService;
import org.springframework.web.bind.annotation.*;

import static com.common.Constant.constant.SUCCESS;

/**
 * 中台任务配置接口。
 */
@RestController
@RequestMapping("/api/platform/schedule-task")
public class PlatformScheduleTaskController {

    private final PlatformScheduleTaskService taskService;

    /**
     * 创建任务配置接口。
     *
     * @param taskService 任务配置服务
     */
    public PlatformScheduleTaskController(PlatformScheduleTaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 查询任务配置。
     *
     * @param query 查询参数
     * @return 任务配置列表
     */
    @PostMapping("/list")
    public ReturnResult list(@RequestBody(required = false) PlatformQuery query) {
        return new ReturnResult<>(SUCCESS, "查询成功", taskService.page(query));
    }

    /**
     * 保存任务配置。
     *
     * @param task 任务配置
     * @return 保存结果
     */
    @PostMapping("/save")
    public ReturnResult<Void> save(@RequestBody PlatformScheduleTask task) {
        taskService.save(task);
        return new ReturnResult<>(SUCCESS, "保存成功");
    }

    /**
     * 删除任务配置。
     *
     * @param id 任务 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ReturnResult<Void> delete(@PathVariable("id") Long id) {
        taskService.delete(id);
        return new ReturnResult<>(SUCCESS, "删除成功");
    }
}

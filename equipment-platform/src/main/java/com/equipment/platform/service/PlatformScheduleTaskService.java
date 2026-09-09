package com.equipment.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipment.platform.dto.PlatformQuery;
import com.equipment.platform.entity.PlatformScheduleTask;
import com.equipment.platform.mapper.PlatformScheduleTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 中台任务配置服务。
 */
@Service
public class PlatformScheduleTaskService {

    private static final List<BuiltinTask> BUILTIN_TASKS = Arrays.asList(
            task("alipay", "alipay.order.close-expired", "支付宝超时订单关闭", "0 */5 * * * ?", "LOCAL",
                    "ScheduledTasks.scheduledTask", "每5分钟扫描超时未支付订单"),
            task("alipay", "alipay.cache.refresh", "支付宝热点缓存刷新", "0 */5 * * * ?", "LOCAL",
                    "ScheduledTasks.refreshMiniappCache", "每5分钟刷新小程序热点缓存"),
            task("alipay", "alipay.goods.status-refresh", "支付宝商品审核状态回查", "0 */5 * * * ?", "LOCAL",
                    "ScheduledTasks.refreshPendingAlipayItemStatuses", "每5分钟回查审核中的支付宝商品状态"),
            task("alipay", "alipay.contract.sync", "支付宝租赁合同回传补偿", "0 0 0 * * ?", "LOCAL",
                    "ScheduledTasks.syncReceivedOrderContracts", "每晚0点扫描确认收货后未成功回传合同的租赁订单，补生成协议并回传支付宝"),
            task("alipay", "alipay.report.weekly", "支付宝租赁周报生成", "0 10 1 ? * MON", "LOCAL",
                    "ScheduledTasks.generateWeeklyReport", "每周一凌晨生成上一自然周报表"),
            task("alipay", "alipay.report.monthly", "支付宝租赁月报生成", "0 20 1 1 * ?", "LOCAL",
                    "ScheduledTasks.generateMonthlyReport", "每月1号凌晨生成上一自然月报表"),
            task("alipay", "alipay.report.yearly", "支付宝租赁年报生成", "0 30 1 1 1 ?", "LOCAL",
                    "ScheduledTasks.generateYearlyReport", "每年1月1日凌晨生成上一自然年报表")
    );

    private final PlatformScheduleTaskMapper taskMapper;

    /**
     * 创建任务配置服务。
     *
     * @param taskMapper 任务配置 Mapper
     */
    public PlatformScheduleTaskService(PlatformScheduleTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /**
     * 启动时补齐代码中实际会读取的内置任务配置；已有记录不覆盖，保留中台页面调整。
     */
    @PostConstruct
    public void ensureBuiltinTasks() {
        Date now = new Date();
        for (BuiltinTask builtin : BUILTIN_TASKS) {
            if (findByBusinessKey(builtin.systemCode, builtin.taskCode) != null) {
                continue;
            }
            PlatformScheduleTask task = new PlatformScheduleTask();
            task.setSystemCode(builtin.systemCode);
            task.setTaskCode(builtin.taskCode);
            task.setTaskName(builtin.taskName);
            task.setCronExpr(builtin.cronExpr);
            task.setExecuteType(builtin.executeType);
            task.setExecuteTarget(builtin.executeTarget);
            task.setEnabled(1);
            task.setRemark(builtin.remark);
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            taskMapper.insert(task);
        }
    }

    /**
     * 查询任务配置。
     *
     * @param query 查询参数
     * @return 任务配置列表
     */
    public List<PlatformScheduleTask> list(PlatformQuery query) {
        PlatformQuery safeQuery = query == null ? new PlatformQuery() : query;
        return taskMapper.selectList(buildListWrapper(safeQuery));
    }

    /**
     * 分页查询任务配置。
     *
     * @param query 查询参数
     * @return 分页任务配置
     */
    public Page<PlatformScheduleTask> page(PlatformQuery query) {
        PlatformQuery safeQuery = query == null ? new PlatformQuery() : query;
        return taskMapper.selectPage(
                new Page<>(safePage(safeQuery), safePageSize(safeQuery)),
                buildListWrapper(safeQuery));
    }

    private QueryWrapper<PlatformScheduleTask> buildListWrapper(PlatformQuery safeQuery) {
        QueryWrapper<PlatformScheduleTask> wrapper = new QueryWrapper<PlatformScheduleTask>()
                .orderByAsc("system_code", "task_code", "id");
        if (StringUtils.hasText(safeQuery.getSystemCode())) {
            wrapper.eq("system_code", safeQuery.getSystemCode().trim());
        }
        if (StringUtils.hasText(safeQuery.getKeyword())) {
            String keyword = safeQuery.getKeyword().trim();
            wrapper.and(w -> w.like("task_code", keyword)
                    .or().like("task_name", keyword)
                    .or().like("remark", keyword));
        }
        return wrapper;
    }

    private int safePage(PlatformQuery query) {
        return query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    }

    private int safePageSize(PlatformQuery query) {
        return query.getPageSize() == null || query.getPageSize() < 1 ? 10 : query.getPageSize();
    }

    /**
     * 按业务运行时读取任务配置原值。
     *
     * @param systemCode 系统编码
     * @param taskCode 任务编码
     * @return 任务配置，未配置时返回 null
     */
    public PlatformScheduleTask getRuntimeTask(String systemCode, String taskCode) {
        if (!StringUtils.hasText(systemCode) || !StringUtils.hasText(taskCode)) {
            throw new IllegalArgumentException("系统编码和任务编码不能为空");
        }
        return taskMapper.selectOne(new QueryWrapper<PlatformScheduleTask>()
                .eq("system_code", systemCode.trim())
                .eq("task_code", taskCode.trim())
                .last("limit 1"));
    }

    /**
     * 新增或更新任务配置。
     *
     * @param task 任务配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(PlatformScheduleTask task) {
        validate(task);
        normalize(task);
        Date now = new Date();
        if (!StringUtils.hasText(task.getExecuteType())) {
            task.setExecuteType("HTTP");
        }
        if (task.getEnabled() == null) {
            task.setEnabled(1);
        }
        task.setUpdatedAt(now);
        if (task.getId() == null) {
            PlatformScheduleTask existing = findByBusinessKey(task.getSystemCode(), task.getTaskCode());
            if (existing == null) {
                task.setCreatedAt(now);
                taskMapper.insert(task);
            } else {
                task.setId(existing.getId());
                task.setCreatedAt(existing.getCreatedAt());
                taskMapper.updateById(task);
            }
            return;
        }
        taskMapper.updateById(task);
    }

    /**
     * 删除任务配置。
     *
     * @param id 任务 ID
     */
    public void delete(Long id) {
        if (id != null) {
            taskMapper.deleteById(id);
        }
    }

    private void validate(PlatformScheduleTask task) {
        if (task == null || !StringUtils.hasText(task.getTaskCode())) {
            throw new IllegalArgumentException("任务编码不能为空");
        }
        if (!StringUtils.hasText(task.getSystemCode())) {
            throw new IllegalArgumentException("系统编码不能为空");
        }
        if (!StringUtils.hasText(task.getCronExpr())) {
            throw new IllegalArgumentException("cron 表达式不能为空");
        }
    }

    private void normalize(PlatformScheduleTask task) {
        task.setSystemCode(task.getSystemCode().trim());
        task.setTaskCode(task.getTaskCode().trim());
        if (StringUtils.hasText(task.getTaskName())) {
            task.setTaskName(task.getTaskName().trim());
        }
        task.setCronExpr(task.getCronExpr().trim());
        if (StringUtils.hasText(task.getExecuteType())) {
            task.setExecuteType(task.getExecuteType().trim());
        }
        if (StringUtils.hasText(task.getExecuteTarget())) {
            task.setExecuteTarget(task.getExecuteTarget().trim());
        }
        if (StringUtils.hasText(task.getRemark())) {
            task.setRemark(task.getRemark().trim());
        }
    }

    private PlatformScheduleTask findByBusinessKey(String systemCode, String taskCode) {
        return taskMapper.selectOne(new QueryWrapper<PlatformScheduleTask>()
                .eq("system_code", systemCode.trim())
                .eq("task_code", taskCode.trim())
                .last("limit 1"));
    }

    private static BuiltinTask task(String systemCode, String taskCode, String taskName, String cronExpr,
                                    String executeType, String executeTarget, String remark) {
        return new BuiltinTask(systemCode, taskCode, taskName, cronExpr, executeType, executeTarget, remark);
    }

    private static class BuiltinTask {
        private final String systemCode;
        private final String taskCode;
        private final String taskName;
        private final String cronExpr;
        private final String executeType;
        private final String executeTarget;
        private final String remark;

        private BuiltinTask(String systemCode, String taskCode, String taskName, String cronExpr,
                            String executeType, String executeTarget, String remark) {
            this.systemCode = systemCode;
            this.taskCode = taskCode;
            this.taskName = taskName;
            this.cronExpr = cronExpr;
            this.executeType = executeType;
            this.executeTarget = executeTarget;
            this.remark = remark;
        }
    }
}

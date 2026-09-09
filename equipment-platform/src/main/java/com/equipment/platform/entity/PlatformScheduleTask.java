package com.equipment.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 中台任务配置实体。
 * <p>
 * 第一版只负责维护任务元数据、cron 和启停状态。业务服务保留实际执行逻辑，
 * 后续可以由中台调度器按配置调用各子系统执行接口。
 * </p>
 */
@Data
@TableName("platform_schedule_task")
public class PlatformScheduleTask {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 任务编码，唯一标识。 */
    @TableField("task_code")
    private String taskCode;

    /** 所属系统编码。 */
    @TableField("system_code")
    private String systemCode;

    /** 任务名称。 */
    @TableField("task_name")
    private String taskName;

    /** cron 表达式。 */
    @TableField("cron_expr")
    private String cronExpr;

    /** 执行类型：HTTP/LOCAL，第一版默认 HTTP。 */
    @TableField("execute_type")
    private String executeType;

    /** 执行地址或业务标识。 */
    @TableField("execute_target")
    private String executeTarget;

    /** 是否启用。 */
    @TableField("enabled")
    private Integer enabled;

    /** 备注。 */
    @TableField("remark")
    private String remark;

    /** 创建时间。 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间。 */
    @TableField("updated_at")
    private Date updatedAt;
}

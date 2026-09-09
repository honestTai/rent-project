package com.equipment.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 中台通用配置实体。
 * <p>
 * 用于承载原先散落在 yml、常量类和业务代码里的可运营配置，例如 OSS、支付宝、飞书、默认联系人、
 * 跳转链接、任务频率、阈值、费率和开关。
 * </p>
 */
@Data
@TableName("platform_config")
public class PlatformConfig {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 系统编码：global/common/alipay。 */
    @TableField("system_code")
    private String systemCode;

    /** 配置分类：notify/oss/alipay/task/business/link 等。 */
    @TableField("config_group")
    private String configGroup;

    /** 配置键，业务代码按该键读取。 */
    @TableField("config_key")
    private String configKey;

    /** 配置值。 */
    @TableField("config_value")
    private String configValue;

    /** 值类型：string/number/boolean/json/secret/cron/url。 */
    @TableField("value_type")
    private String valueType;

    /** 是否敏感配置，敏感配置前端列表默认脱敏。 */
    @TableField("secret_flag")
    private Integer secretFlag;

    /** 是否启用。 */
    @TableField("enabled")
    private Integer enabled;

    /** 展示名称。 */
    @TableField("display_name")
    private String displayName;

    /** 配置说明。 */
    @TableField("remark")
    private String remark;

    /** 排序值。 */
    @TableField("sort")
    private Integer sort;

    /** 创建时间。 */
    @TableField("created_at")
    private Date createdAt;

    /** 更新时间。 */
    @TableField("updated_at")
    private Date updatedAt;
}

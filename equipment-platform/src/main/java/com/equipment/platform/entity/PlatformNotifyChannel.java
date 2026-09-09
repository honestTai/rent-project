package com.equipment.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 中台通知通道实体。
 * <p>
 * 用于拆分飞书机器人和后续其他通知渠道。不同系统、不同场景可以绑定不同机器人，
 * 例如周期报表、支付宝订单异常、系统告警。
 * </p>
 */
@Data
@TableName("platform_notify_channel")
public class PlatformNotifyChannel {

    /** 主键。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 通道编码。 */
    @TableField("channel_code")
    private String channelCode;

    /** 系统编码：common/alipay。 */
    @TableField("system_code")
    private String systemCode;

    /** 场景编码：report_created、alipay_order_exception、system_alert 等。 */
    @TableField("scene_code")
    private String sceneCode;

    /** 通道类型：feishu。 */
    @TableField("channel_type")
    private String channelType;

    /** 飞书应用 appId。 */
    @TableField("app_id")
    private String appId;

    /** 飞书应用 appSecret。 */
    @TableField("app_secret")
    private String appSecret;

    /** 接收者类型。 */
    @TableField("receive_id_type")
    private String receiveIdType;

    /** 接收者 ID，例如 chat_id。 */
    @TableField("receive_id")
    private String receiveId;

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

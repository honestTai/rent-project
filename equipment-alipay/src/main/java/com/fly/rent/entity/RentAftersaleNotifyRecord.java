package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 支付宝售后通知落库表。
 *
 * 设计目的：
 * 1. 把支付宝推送的 notify_id 持久化，作为通知幂等基线；
 * 2. 保存原始报文和处理结果，便于后续排查“为什么这次赔付没有落账”；
 * 3. 避免把通知处理状态混入业务表，保持业务台账与通知流水边界清晰。
 */
@Data
@TableName("rent_aftersale_notify_record")
public class RentAftersaleNotifyRecord extends Model<RentAftersaleNotifyRecord> {

    public static final String PROCESS_STATUS_PROCESSING = "PROCESSING";
    public static final String PROCESS_STATUS_SUCCESS = "SUCCESS";
    public static final String PROCESS_STATUS_FAILED = "FAILED";

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("notify_id")
    private String notifyId;

    @TableField("msg_method")
    private String msgMethod;

    @TableField("out_order_id")
    private String outOrderId;

    @TableField("order_id")
    private String orderId;

    @TableField("aftersale_id")
    private String aftersaleId;

    @TableField("out_aftersale_id")
    private String outAftersaleId;

    @TableField("aftersale_status")
    private String aftersaleStatus;

    @TableField("operation_type")
    private String operationType;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("process_status")
    private String processStatus;

    @TableField("fail_reason")
    private String failReason;

    @TableField("created_at")
    private Long createTime;

    @TableField("updated_at")
    private Date updateTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}

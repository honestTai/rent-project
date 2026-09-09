package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;

import java.io.Serializable;

/**
 * 订单操作日志表。
 */
@Data
@TableName("order_operation_log")
public class OrderOperLog extends Model<OrderOperLog> {

    private static final long serialVersionUID = 1L;

    @TableId(value = "log_id", type = IdType.AUTO)
    private Integer orderOperLogId;

    @TableField("order_id")
    private Integer orderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("operation_type")
    private String operType;

    @TableField("operation_desc")
    private String operDesc;

    @TableField("status_before")
    private String beforeStatus;

    @TableField("status_after")
    private String afterStatus;

    @TableField("request_body")
    private String requestBody;

    @TableField("response_body")
    private String resultBody;

    private Integer success;

    @TableField("fail_reason")
    private String failReason;

    private String operator;

    @TableField("created_at")
    private Long createtime;

    @Override
    public Serializable pkVal() {
        return this.orderOperLogId;
    }
}

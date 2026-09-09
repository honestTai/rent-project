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
 * 押金扣减记录表。
 */
@Data
@TableName("rent_deposit_deduct_record")
public class RentDepositDeductRecord extends Model<RentDepositDeductRecord> {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("order_id")
    private Integer orderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("auth_no")
    private String authNo;

    @TableField("aftersale_no")
    private String aftersaleNo;

    @TableField("out_aftersale_id")
    private String outAftersaleId;

    @TableField("confirm_request_no")
    private String confirmRequestNo;

    @TableField("out_trade_no")
    private String outTradeNo;

    @TableField("operation_no")
    private String operationNo;

    @TableField("trade_no")
    private String tradeNo;

    @TableField("out_request_no")
    private String outRequestNo;

    @TableField("fee_type")
    private String feeType;

    @TableField("reason_code")
    private String reasonCode;

    @TableField("deduct_amount")
    private Integer deductAmount;

    @TableField("before_remaining_deposit")
    private Integer beforeRemainingDeposit;

    @TableField("after_remaining_deposit")
    private Integer afterRemainingDeposit;

    private String reason;

    private String remark;

    @TableField("operator_id")
    private String operatorId;

    @TableField("operator_name")
    private String operatorName;

    @TableField("alipay_sub_code")
    private String alipaySubCode;

    @TableField("alipay_sub_msg")
    private String alipaySubMsg;

    /**
     * 支付宝售后单当前状态。
     * 该字段用于同步官方售后通知里的 aftersale_status，
     * 让后台能看到“审核中 / 成功 / 失败”等售后节点，而不是只看本地扣减状态。
     */
    @TableField("aftersale_status")
    private String aftersaleStatus;

    /**
     * 支付宝售后通知里的本次操作类型。
     * 该字段可以帮助排查当前这条记录是由“用户申请、商户同意、用户撤销、售后成功”等哪一步触发的。
     */
    @TableField("last_operation_type")
    private String lastOperationType;

    /**
     * 支付宝售后通知里的来源类型。
     * 常见值为 ZHIMA_RENT / MERCHANT，用来区分售后是用户发起还是商家发起。
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 支付宝售后通知里的下一步是否需要商家操作标识。
     * 这里直接保留字符串值，避免后续扩展时被布尔值约束住。
     */
    @TableField("need_operation")
    private String needOperation;

    /**
     * 最近一次成功消费的售后通知 ID。
     * 该字段主要用于问题排查和人工核对，真正的通知幂等会落到独立通知表里。
     */
    @TableField("last_notify_id")
    private String lastNotifyId;

    private String status;

    @TableField("created_at")
    private Long createTime;

    @TableField("updated_at")
    private Date updateTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}

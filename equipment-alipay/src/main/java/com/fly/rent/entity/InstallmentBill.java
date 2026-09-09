package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

@Data
@Accessors(chain = true)
@TableName("installment_bill")
public class InstallmentBill {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Integer orderId;

    @TableField("bill_no")
    private String billNo;

    @TableField("period_no")
    private Integer periodNo;

    @TableField("period_total")
    private Integer periodTotal;

    private Integer amount;

    @TableField("paid_amount")
    private Integer paidAmount;

    @TableField("due_date")
    private Date dueDate;

    @TableField("paid_at")
    private Date paidAt;

    private String status;

    @TableField("payment_trade_no")
    private String paymentTradeNo;

    @TableField("out_trade_no")
    private String outTradeNo;

    @TableField("last_error")
    private String lastError;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;
}

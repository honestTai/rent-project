package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * 分期计划表。
 */
@Data
@Accessors(chain = true)
@TableName("installment_plan")
public class RentInstallmentInfoEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("buyout_price")
    private String buyoutPrice;

    @TableField("period_no")
    private Long installmentNo;

    @TableField("period_amount")
    private String installmentPrice;

    @TableField("plan_pay_time")
    private Date planPayTime;

    @TableField("order_id")
    private Integer orderId;

    private Integer status;
}

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
@TableName("rent_buyout_payment")
public class RentBuyoutPayment {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Integer orderId;

    @TableField("order_no")
    private String orderNo;

    @TableField("rent_order_id")
    private String rentOrderId;

    @TableField("attr_id")
    private Integer attrId;

    @TableField("user_uuid")
    private String userUuid;

    @TableField("buyout_price")
    private Integer buyoutPrice;

    @TableField("out_installment_order_id")
    private String outInstallmentOrderId;

    @TableField("installment_order_id")
    private String installmentOrderId;

    @TableField("payment_trade_no")
    private String paymentTradeNo;

    private String status;

    @TableField("last_error")
    private String lastError;

    @TableField("raw_response")
    private String rawResponse;

    @TableField("raw_notify")
    private String rawNotify;

    @TableField("paid_at")
    private Date paidAt;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;
}

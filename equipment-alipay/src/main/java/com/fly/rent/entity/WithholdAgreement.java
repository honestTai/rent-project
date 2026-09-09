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
@TableName("withhold_agreement")
public class WithholdAgreement {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Integer orderId;

    @TableField("user_id")
    private String userId;

    private String provider;

    @TableField("agreement_no")
    private String agreementNo;

    @TableField("external_agreement_no")
    private String externalAgreementNo;

    private String status;

    @TableField("sign_url")
    private String signUrl;

    @TableField("sign_str")
    private String signStr;

    @TableField("signed_at")
    private Date signedAt;

    @TableField("next_deduct_at")
    private Date nextDeductAt;

    @TableField("raw_response")
    private String rawResponse;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;
}

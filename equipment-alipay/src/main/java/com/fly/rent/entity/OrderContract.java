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
@TableName("order_contract")
public class OrderContract {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Integer orderId;

    private String provider;

    @TableField("contract_no")
    private String contractNo;

    @TableField("flow_id")
    private String flowId;

    @TableField("signer_id")
    private String signerId;

    @TableField("file_id")
    private String fileId;

    @TableField("contract_name")
    private String contractName;

    private String status;

    @TableField("sign_url")
    private String signUrl;

    @TableField("view_url")
    private String viewUrl;

    @TableField("download_url")
    private String downloadUrl;

    @TableField("pdf_url")
    private String pdfUrl;

    @TableField("signed_at")
    private Date signedAt;

    @TableField("expire_at")
    private Date expireAt;

    @TableField("raw_response")
    private String rawResponse;

    @TableField("created_at")
    private Date createdAt;

    @TableField("updated_at")
    private Date updatedAt;
}

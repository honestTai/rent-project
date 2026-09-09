package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** 小程序目录后台操作审计；与支付宝商品及订单日志相互独立。 */
@Data
@TableName("rent_catalog_admin_operation")
public class CatalogAdminOperation {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String idempotencyKey;
    private String action;
    private String resourceCode;
    private String operatorName;
    private String requestSummary;
    private String resultSummary;
    private Integer success;
    private String failReason;
    private Date createdAt;
    private Date updatedAt;
}

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
 * 支付宝租赁售后同步快照。
 *
 * 该表只保存支付宝侧售后单的最近一次查询结果，用于后台预览差异和人工导入；
 * 不直接代表本地已经扣款，真正可操作的业务台账仍然是 rent_deposit_deduct_record。
 */
@Data
@TableName("rent_aftersale_sync_snapshot")
public class RentAftersaleSyncSnapshot extends Model<RentAftersaleSyncSnapshot> {

    public static final String MATCH_STATUS_MATCHED = "MATCHED";
    public static final String MATCH_STATUS_MISSING_LOCAL = "MISSING_LOCAL";
    public static final String MATCH_STATUS_QUERY_FAILED = "QUERY_FAILED";

    public static final String IMPORT_STATUS_PENDING = "PENDING_IMPORT";
    public static final String IMPORT_STATUS_IMPORTED = "IMPORTED";
    public static final String IMPORT_STATUS_FAILED = "FAILED";

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 本地租赁订单 ID。
     */
    @TableField("order_id")
    private Integer orderId;

    /**
     * 本地租赁订单号。
     */
    @TableField("order_no")
    private String orderNo;

    /**
     * 支付宝租赁交易组件订单号。
     */
    @TableField("rent_order_id")
    private String rentOrderId;

    /**
     * 支付宝售后单号。
     */
    @TableField("aftersale_no")
    private String aftersaleNo;

    /**
     * 商户外部售后单号。
     */
    @TableField("out_aftersale_id")
    private String outAftersaleId;

    /**
     * 售后来源，常见值为 MERCHANT / ZHIMA_RENT。
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 售后类型，例如 COMPENSATION。
     */
    @TableField("aftersale_type")
    private String aftersaleType;

    /**
     * 支付宝售后状态。
     */
    @TableField("aftersale_status")
    private String aftersaleStatus;

    /**
     * 支付宝侧是否已完结。
     */
    @TableField("finished")
    private Boolean finished;

    /**
     * 支付宝侧售后申请时间。
     */
    @TableField("apply_time")
    private Date applyTime;

    /**
     * 最近一次操作类型。
     */
    @TableField("last_operation_type")
    private String lastOperationType;

    /**
     * 支付宝返回的下一步可操作动作，逗号分隔。
     */
    @TableField("next_operation_types")
    private String nextOperationTypes;

    /**
     * 是否需要商家处理，Y/N。
     */
    @TableField("need_operation")
    private String needOperation;

    /**
     * 最近一次操作里的费用类型。
     */
    @TableField("fee_type")
    private String feeType;

    /**
     * 根据支付宝原因描述推断出的本地原因码。
     */
    @TableField("reason_code")
    private String reasonCode;

    /**
     * 售后金额，单位分。
     */
    @TableField("deduct_amount")
    private Integer deductAmount;

    /**
     * 支付宝原因描述或补充说明。
     */
    @TableField("reason_description")
    private String reasonDescription;

    /**
     * 关联到的本地扣减记录 ID。
     */
    @TableField("matched_deduct_record_id")
    private Integer matchedDeductRecordId;

    /**
     * 对账状态：MATCHED / MISSING_LOCAL / QUERY_FAILED。
     */
    @TableField("match_status")
    private String matchStatus;

    /**
     * 导入状态：PENDING_IMPORT / IMPORTED / FAILED。
     */
    @TableField("import_status")
    private String importStatus;

    /**
     * 支付宝原始售后快照 JSON。
     */
    @TableField("payload_json")
    private String payloadJson;

    /**
     * 同步或导入失败原因。
     */
    @TableField("fail_reason")
    private String failReason;

    @TableField("synced_at")
    private Date syncedAt;

    @TableField("imported_at")
    private Date importedAt;

    @TableField("created_at")
    private Long createTime;

    @TableField("updated_at")
    private Date updateTime;

    @Override
    public Serializable pkVal() {
        return this.id;
    }
}

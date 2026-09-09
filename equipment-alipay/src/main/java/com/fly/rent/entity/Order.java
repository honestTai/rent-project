package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 租赁订单表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("rent_order")
public class Order extends Model<Order> {

    private static final long serialVersionUID = 1L;

    @TableId(value = "order_id", type = IdType.AUTO)
    private Integer orderId;

    @TableField("sku_id")
    private Integer attrId;

    @TableField("sku_title")
    private String attrTitle;

    @TableField("daily_rent")
    private Integer attrAmount;

    @TableField("order_no")
    private String orderNo;

    @TableField("request_no")
    private String orderRequestNo;

    @TableField("auth_no")
    private String orderAuthNo;

    /**
     * 支付宝租赁租金支付交易号。
     * 该字段专门保存 rent.order.pay 产生的 trade_no，
     * 与资金授权号 auth_no 分离，避免冻结授权号与支付交易号混用。
     *
     * 兼容约束：
     * 1. 该字段允许为空，历史订单不要求立即回填；
     * 2. 读取交易号时，业务代码必须支持回退到旧字段；
     * 3. 新订单和新支付通知优先写入该字段。
     */
    @TableField("payment_trade_no")
    private String paymentTradeNo;

    @TableField("operation_no")
    private String orderOperationNo;

    @TableField("quantity")
    private Integer attrNum;

    @TableField("deposit")
    private Integer orderDeposit;

    @TableField("damage_fee")
    private Integer orderDamage;

    @TableField("delivery_type")
    private Integer goodDistr;

    @TableField("goods_cover")
    private String goodCover;

    @TableField("overdue_fee")
    private Integer orderBeamo;

    @TableField("paid_amount")
    private Integer orderFinalpay;

    @TableField("total_amount")
    private Integer orderTotal;

    @TableField("goods_id")
    private Integer goodId;

    @TableField("goods_title")
    private String goodTitle;

    @TableField("rent_start")
    private Long orderStart;

    @TableField("rent_end")
    private Long orderEnd;

    @TableField("rent_days")
    private Integer orderKeep;

    @TableField("created_at")
    private Long createtime;

    @TableField("message")
    private String orderLeave;

    @TableField("last_paid_at")
    private Long orderLastpay;

    @TableField("express_no")
    private String courno;

    @TableField("express_company")
    private String courName;

    @TableField("express_code")
    private String courCode;

    @TableField("user_id")
    private Integer userId;

    @TableField("user_name")
    private String userTitle;

    @TableField("user_phone")
    private String userTel;

    @TableField("address")
    private String addr;

    @TableField("bill_info")
    private String orderWxBillInfo;

    private Integer status;

    @TableField("received_at")
    private Long orderReciveTime;

    @TableField("shipped_at")
    private Long orderSendTime;

    @TableField("next_billing_at")
    private Long orderNextTradeTime;

    @TableField("billing_cycle")
    private Integer orderTradeType;

    @TableField("penalty_per_day")
    private Integer penalAmount;

    @TableField("current_period")
    private Integer orderRentPeriods;

    @TableField("user_uuid")
    private String userUuid;

    @TableField("coupon_info")
    private String ticketInfo;

    @TableField("remaining_coupon")
    private Integer remainingCouponAmount;

    @TableField("total_periods")
    private Integer orderTotalRentPeriods;

    @TableField("order_type")
    private Integer orderMyType;

    @TableField("rent_to_own")
    private Integer rentToSend;

    @TableField("first_period_amount")
    private Integer orderFirstAmount;

    @TableField("per_period_amount")
    private Integer orderEveryAmount;

    @TableField("last_period_amount")
    private Integer orderEndAmount;

    @TableField("remaining_deposit")
    private Integer orderRestDeposit;

    @TableField("billing_date")
    private String orderTradeDate;

    private Integer freight;

    @TableField("support_pickup")
    private Integer offlinePickup;

    @TableField("pickup_address")
    private String pickupAddr;

    @TableField("return_info")
    private String returnInfo;

    @TableField("id_card_front")
    private String em;

    @TableField("id_card_portrait")
    private String avatar;

    @TableField(exist = false)
    private String amount;

    @TableField("frozen_credit")
    private String totalFreezeCreditAmount;

    @TableField("remaining_credit")
    private String restCreditAmount;

    @TableField("frozen_fund")
    private String totalFreezeFundAmount;

    @TableField("paid_credit")
    private String totalPayCreditAmount;

    @TableField("paid_fund")
    private String totalPayFundAmount;

    @TableField("remaining_fund")
    private String restFundAmount;

    @TableField("total_paid")
    private String totalPayAmount;

    @TableField("remaining_frozen")
    private String restAmount;

    private String remark;

    @TableField("updated_at")
    private Date updateTime;

    @TableField(exist = false)
    private String aplyOrderNo;

    @TableField(exist = false)
    private String aplyOrderAuthNo;

    @TableField("alipay_order_id")
    private String rentOrderId;

    @TableField("merchant_order_id")
    private String outOrderId;

    @TableField("source_id")
    private String sourceId;

    @TableField(exist = false)
    private String rentSendStatus;

    @TableField("alipay_status")
    private String alipayStatus;

    @TableField("contract_no")
    private String contractNo;

    @TableField("contract_version")
    private String contractVersion;

    @TableField("contract_agreed_at")
    private Long contractAgreedAt;

    @TableField("contract_pdf_url")
    private String contractPdfUrl;

    @TableField("contract_pdf_path")
    private String contractPdfPath;

    @TableField("contract_snapshot_json")
    private String contractSnapshotJson;

    /** 支付宝文件上传接口返回的合同 PDF file_id，用于幂等和人工排查。 */
    @TableField("contract_alipay_file_id")
    private String contractAlipayFileId;

    /** 合同回传支付宝状态：SUCCESS/FAILED，空表示尚未尝试。 */
    @TableField("contract_alipay_sync_status")
    private String contractAlipaySyncStatus;

    /** 最近一次合同回传支付宝的时间戳，成功和失败都会记录。 */
    @TableField("contract_alipay_synced_at")
    private Long contractAlipaySyncedAt;

    /** 最近一次合同回传支付宝失败原因，成功后清空。 */
    @TableField("contract_alipay_sync_error")
    private String contractAlipaySyncError;

    @TableField(exist = false)
    private Boolean isAgree;

    @TableField(exist = false)
    private String originalOrderId;

    @TableField(exist = false)
    private String returnType;

    /**
     * 用户寄回记录，仅用于后台订单列表和详情展示，不映射 rent_order 表字段。
     * 该对象来自 rent_order_return_record，表示用户寄回物流、照片和说明。
     */
    @TableField(exist = false)
    private RentOrderReturnRecord latestReturnRecord;

    @TableField(exist = false)
    private Boolean idCardPhotoRequired;

    @TableField(exist = false)
    private Boolean esignRequired;

    @TableField(exist = false)
    private List<String> idCardPhotoUrls;

    @TableField(exist = false)
    private Long idCardPhotoUploadedAt;

    @TableField(exist = false)
    private String idCardPhotoReviewStatus;

    @TableField(exist = false)
    private String idCardPhotoReviewRemark;

    @TableField(exist = false)
    private Long idCardPhotoReviewedAt;

    @TableField(exist = false)
    private String accountUserTel;

    @TableField(exist = false)
    private String accountRealName;

    @TableField(exist = false)
    private String accountIdCard;

    @Override
    public Serializable pkVal() {
        return this.orderId;
    }
}

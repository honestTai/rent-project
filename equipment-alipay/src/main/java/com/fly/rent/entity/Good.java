package com.fly.rent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 商品表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("goods")
public class Good extends Model<Good> {

    private static final long serialVersionUID = 1L;

    @TableId(value = "goods_id", type = IdType.AUTO)
    private Integer goodId;

    @TableField("title")
    private String goodTitle;

    @TableField("description")
    private String goodDesc;

    @TableField("cover")
    private String goodCover;

    @TableField("slides")
    private String goodSlid;

    @TableField("min_price")
    private Integer goodMinamo;

    @TableField("max_price")
    private Integer goodMaxamo;

    @TableField("deposit")
    private Integer goodDeposit;

    @TableField("delivery_type")
    private Integer goodDistr;

    @TableField("activity_text")
    private String goodAct;

    @TableField("spec_params")
    private String goodSpepar;

    @TableField("detail")
    private String goodCon;

    @TableField("rental_guide")
    private String goodRec;

    @TableField("alipay_goods_id")
    private String alipayGoodsId;

    /** 支付宝普通商品类目 ID，多级类目只保存最后一级。 */
    @TableField("alipay_category_id")
    private String alipayCategoryId;

    /** 支付宝芝麻信用预授权租赁类目 ID，用于创建租赁订单。 */
    @TableField("alipay_rent_category_id")
    private String alipayRentCategoryId;

    /** 支付宝商品成色：wholeNew 全新，secondHand 二手。 */
    @TableField("item_fineness")
    private String itemFineness;

    /** 支付宝商品成色等级，例如 95new。 */
    @TableField("item_fineness_grade")
    private String itemFinenessGrade;

    /** 支付宝商品审核状态，来源于 alipay.open.app.item.status.notify 的 audit_status。 */
    @TableField("alipay_audit_status")
    private String alipayAuditStatus;

    /** 支付宝商品 SPU 状态，来源于商品查询或状态通知中的 spu_status。 */
    @TableField("alipay_spu_status")
    private String alipaySpuStatus;

    /** 支付宝商品状态原因，主要保存审核驳回、冻结等场景的 reasons 原文。 */
    @TableField("alipay_status_reason")
    private String alipayStatusReason;

    /** 支付宝商品状态最近更新时间，来源于状态通知时间或本地处理时间。 */
    @TableField("alipay_status_updated_at")
    private Date alipayStatusUpdatedAt;

    /** 最新一次支付宝商品同步任务状态，来自 alipay_goods_sync_log。 */
    @TableField(exist = false)
    private String alipaySyncStatus;

    /** 最新一次支付宝商品同步日志，来自 alipay_goods_sync_log。 */
    @TableField(exist = false)
    private String alipaySyncLog;

    /** 最新一次支付宝商品同步开始时间，来自 alipay_goods_sync_log。 */
    @TableField(exist = false)
    private Date alipaySyncStartedAt;

    /** 最新一次支付宝商品同步结束时间，来自 alipay_goods_sync_log。 */
    @TableField(exist = false)
    private Date alipaySyncFinishedAt;

    private int status;

    @TableField("is_public")
    private int ispub;

    private String qrcode;

    @TableField("sales")
    private Integer deal;

    @TableField("sort_order")
    private Long goodSort;

    /** 稳定叶子分类编码。 */
    @TableField("category_code")
    private String categoryCode;

    /** 品牌。 */
    private String brand;

    /** 型号。 */
    @TableField("model_name")
    private String modelName;

    /** 设备类型。 */
    @TableField("device_type")
    private String deviceType;

    /** 默认报价展示单位：DAY/MONTH。 */
    @TableField("default_rent_unit")
    private String defaultRentUnit;

    /** 是否首页推荐。 */
    private Integer featured;

    /** 首页推荐排序。 */
    @TableField("featured_sort")
    private Long featuredSort;

    @TableField("support_pickup")
    private Integer offlinePickup;

    private Integer freight;

    @TableField("support_buyout")
    private Integer isBuyOut;


    @TableField("alipay_image_dir_id")
    private String imageDirectoryId;

    @Override
    public Serializable pkVal() {
        return this.goodId;
    }
}

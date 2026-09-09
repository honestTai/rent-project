package com.fly.rent.common.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单扩展信息。
 * 这里存的是新文档要求、但旧订单表暂时没有的字段。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Data
public class RentOrderExtension implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 取货方式
     */
    private String pickupType;
    /**
     * 租期单位
     */
    private String rentUnit;
    /**
     * 地址信息
     */
    private RentAddressSnapshot addressInfo;
    /**
     * 插件原始参数JSON
     */
    private String pluginRawParamsJson;

    /**
     * 订单类型。
     * 为空时表示沿用历史普通租赁单；RELET 表示当前订单是基于原单创建的续租子单。
     */
    private String orderType;

    /**
     * 父单本地订单号。
     * 主要用于后台排查“这笔续租单是从哪一笔本地订单派生出来的”。
     */
    private String parentOrderNo;

    /**
     * 父单支付宝租赁订单号。
     * 对于首次续租，它与 originRentOrderId 一致；
     * 对于多次续租，它表示“上一层父单”，用于组装 parent_order_id。
     */
    private String parentRentOrderId;

    /**
     * 原始根单的支付宝租赁订单号。
     * 多次续租时需要一路向上追溯到最初那一笔原单，
     * 用于组装 relet_info.origin_order_id，避免链路断裂。
     */
    private String originRentOrderId;

    /**
     * 后台审核通过时是否要求用户支付前上传身份证照片。
     */
    private Boolean idCardPhotoRequired;

    /**
     * 后台审核通过时是否要求用户确认收货前完成 e签宝电子合同签署。
     */
    private Boolean esignRequired;

    /**
     * 用户上传的身份证照片 OSS 相对路径或完整 URL。
     */
    private List<String> idCardPhotoUrls = new ArrayList<>();

    /**
     * 用户最近一次上传身份证照片的时间戳。
     */
    private Long idCardPhotoUploadedAt;

    /**
     * 身份证照片审核状态。
     * PENDING_UPLOAD: 待用户上传；PENDING_REVIEW: 待后台审核；APPROVED: 审核通过；REJECTED: 审核不通过。
     */
    private String idCardPhotoReviewStatus;

    /**
     * 身份证照片审核备注，审核不通过时返回给小程序用户重新上传参考。
     */
    private String idCardPhotoReviewRemark;

    /**
     * 身份证照片最近一次后台审核时间戳。
     */
    private Long idCardPhotoReviewedAt;
}

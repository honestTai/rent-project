package com.fly.rent.common.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 统一收口租赁接口的请求对象。
 * 这样做可以避免请求模型散落在多个模块里，后续字段扩展时也更容易统一维护。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public final class RentRequests {

    private RentRequests() {
    }

    /**
     * 小程序支付宝静默登录请求。
     */
    @Data
    public static class AlipayLoginRequest {
        /**
         * 授权码
         */
        private String authCode;
        /**
         * 平台
         */
        private String platform;
        /**
         * 设备ID
         */
        private String deviceId;
        /**
         * 客户端版本
         */
        private String clientVersion;
    }

    /**
     * 小程序用户资料修改请求。
     */
    @Data
    public static class UserProfileUpdateRequest {
        /**
         * 用户昵称
         */
        private String userTitle;
        /**
         * 支付宝昵称别名。
         */
        private String nickName;
        /**
         * 用户头像
         */
        private String userAvatar;
        /**
         * 用户头像别名。
         */
        private String avatar;
        /**
         * 用户电话
         */
        private String userTel;
        /**
         * 用户生日
         */
        private String userBirth;
        /**
         * 城市
         */
        private String city;
    }

    /**
     * 支付宝获取手机号更新请求。
     * 前端通过 my.getPhoneNumber() 获取的加密数据，原样放入 response 字段传给后端解密并更新用户手机号。
     */
    @Data
    public static class AlipayPhoneNumberUpdateRequest {
        /**
         * 支付宝返回的完整报文，兼容 JSON 对象或 JSON 字符串。
         * 示例：{"response":"...","sign":"..."}
         */
        private JsonNode response;
    }

    /**
     * 实人认证初始化请求（获取 certifyId + url 供小程序 my.startAPVerify 使用）。
     */
    @Data
    public static class CertifyInitRequest {
        private String name;
        private String idCard;
    }

    /**
     * 实名认证请求。
     * 若传 certifyId：先调支付宝 alipay.user.certify.open.query 查实人认证结果，通过后再落库。
     */
    @Data
    public static class RealNameVerifyRequest {
        /**
         * 实人认证流程号（小程序 my.startAPVerify 完成后带回，有则先查支付宝再落库）
         */
        private String certifyId;
        /**
         * 姓名
         */
        private String name;
        /**
         * 身份证号
         */
        private String idCard;
        /**
         * 关联订单ID（可选，从订单详情进入认证时传入，认证成功后自动回填订单身份信息）
         */
        private String orderId;
    }

    /**
     * 交易组件预览请求。
     */
    @Data
    public static class TradePreviewRequest {
        /**
         * 商品ID
         */
        private String goodId;
        /**
         * 规格ID
         */
        private String skuId;
        /**
         * 租期
         */
        private Integer duration;
        /**
         * 租期单位
         */
        private String rentUnit;
        /**
         * 取货方式
         */
        private String pickupType;
        /**
         * 地址信息
         */
        private RentViews.AddressView addressInfo;
    }

    /**
     * 交易组件建单请求。
     */
    @Data
    public static class TradeOrderCreateRequest {
        /**
         * 来源ID
         */
        private String sourceId;
        /**
         * 商品ID
         */
        private String goodId;
        /**
         * 规格ID
         */
        private String skuId;
        /**
         * 租期
         */
        private Integer duration;
        /**
         * 租期单位
         */
        private String rentUnit;
        /**
         * 取货方式
         */
        private String pickupType;
        /**
         * 地址信息
         */
        private RentViews.AddressView addressInfo;
        /**
         * 插件原始参数
         */
        private JsonNode pluginRawParams;
    }

    /**
     * 商品列表查询请求。
     * 正式商品列表查询请求。
     */
    @Data
    public static class GoodsQueryRequest {
        /**
         * 页码
         */
        private Integer page;
        /**
         * 每页数量
         */
        private Integer pageSize;
        /**
         * 关键词
         */
        private String keyword;
        /** 稳定分类编码。 */
        private String categoryCode;

        /** 查询父分类时是否包含全部启用后代。 */
        private Boolean includeDescendants;

        /** 是否只返回至少一个 SKU 有库存的商品。 */
        private Boolean onlyAvailable;

        /** DEFAULT/PRICE/SALES/NEWEST。 */
        private String sortBy;

        /** ASC/DESC。 */
        private String sortOrder;
    }

    /** 公开分类树查询请求。 */
    @Data
    public static class CategoriesQueryRequest {
        private Boolean includeChildren;
        private Boolean includeGoodsCount;
    }

    /**
     * 小程序订单列表查询请求。
     */
    @Data
    public static class OrderQueryRequest {
        /**
         * 页码
         */
        private Integer page;
        /**
         * 每页数量
         */
        private Integer pageSize;
        /**
         * 筛选类型：all-全部，pending-待处理，renting-租赁中，finished-已完成
         */
        private String tab;
        /**
         * 支付宝状态（单状态筛选，与 tab 二选一时优先使用 tab）
         */
        private String alipayStatus;
        /**
         * 关键词
         */
        private String keyword;
    }

    /**
     * 小程序订单动作请求。
     */
    @Data
    public static class OrderActionRequest {
        /**
         * 动作
         */
        private String action;
        /**
         * 续租追踪ID。
         * 续租本质上仍然是支付宝租赁交易组件的建单动作，官方模型中的 source_id 会影响归因链路，
         * 因此前端若能重新调用 my.checkBeforeAddOrder 获取新的 sourceId，应优先透传到后端。
         */
        private String sourceId;
        /**
         * 续租时长
         */
        private Integer renewDuration;
        /**
         * 备注
         */
        private String remark;
    }

    /**
     * 支付宝租赁订单状态回调请求。
     */
    @Data
    public static class AlipayOrderStatusCallbackRequest {
        /**
         * 来源ID
         */
        private String sourceId;
        /**
         * 租赁订单ID
         */
        private String rentOrderId;
        /**
         * 订单ID
         */
        private String orderId;
        /**
         * 支付宝状态
         */
        private String alipayStatus;
        /**
         * 合约状态
         */
        private String contractStatus;
        /**
         * 支付状态
         */
        private String payStatus;
        /**
         * 发生时间
         */
        private String occurredAt;
        /**
         * 原始报文
         */
        private JsonNode rawBody;
    }

    /**
     * 后台直接回写订单状态的请求。
     */
    @Data
    public static class AdminOrderStatusUpdateRequest {
        /**
         * 支付宝状态
         */
        private String alipayStatus;
        /**
         * 发货时间
         */
        private String deliverAt;
        /**
         * 备注
         */
        private String remark;
    }

    /**
     * 后台审核确认请求。
     */
    @Data
    public static class AdminMerchantConfirmRequest {
        /**
         * 是否同意
         */
        private Boolean agree;
        /**
         * 备注
         */
        private String remark;
    }

    /**
     * 后台退款处理请求。
     */
    @Data
    public static class AdminRefundRequest {
        /**
         * 是否同意
         */
        private Boolean agree;
        /**
         * 备注
         */
        private String remark;
    }

    /**
     * 后台发货请求。
     */
    @Data
    public static class AdminDeliveryCreateRequest {
        /**
         * 发货类型
         */
        private String deliveryType;
        /**
         * 发货ID
         */
        private String deliveryId;
        /**
         * 运单ID
         */
        private String waybillId;
        /**
         * 快递公司名称
         */
        private String courierName;
        /**
         * 快递单号
         */
        private String courierNo;
        /**
         * 备注
         */
        private String remark;
    }

    /**
     * 后台签收请求。
     */
    @Data
    public static class AdminReceiptConfirmRequest {
        /**
         * 签收类型
         */
        private String receiptType;
        /**
         * 备注
         */
        private String remark;
    }

    /**
     * 后台完结请求。
     */
    @Data
    public static class AdminFinishRequest {
        /**
         * 完结状态
         */
        private String finishStatus;
        /**
         * 备注
         */
        private String remark;
    }
}

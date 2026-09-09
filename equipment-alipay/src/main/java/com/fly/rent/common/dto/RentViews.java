package com.fly.rent.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 统一收口租赁接口的返回视图对象。
 * 控制器只负责选择返回哪个视图，字段组装都放在应用服务和组装器里。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
public final class RentViews {

    private RentViews() {
    }

    /**
     * 通用分页结果。
     */
    @Data
    public static class PageData<T> {
        /**
         * 列表数据
         */
        private List<T> list = Collections.emptyList();
        /**
         * 总数
         */
        private long total;
    }

    /**
     * 首页轮播图视图。
     */
    @Data
    public static class BannerView {
        /**
         * 轮播图ID
         */
        private String bannerId;
        /**
         * 标题
         */
        private String title;
        /**
         * 描述
         */
        private String desc;
        /**
         * 徽章
         */
        private String badge;
        /**
         * 图片URL
         */
        private String image;
        /**
         * 跳转类型：none、goods、path、url
         */
        private String linkType;
        /**
         * 跳转值：商品ID、小程序路径或外部URL
         */
        private String linkValue;
        /**
         * 排序
         */
        private Integer sort;
        /**
         * 状态
         */
        private Integer status;
    }

    /**
     * 地址快照视图。
     */
    @Data
    public static class AddressView implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 城市
         */
        private String city;
        /**
         * 区域
         */
        private String district;
        /**
         * 详细地址
         */
        private String detail;
        /**
         * 收货人
         */
        private String consignee;
        /**
         * 手机号
         */
        private String mobile;
    }

    /**
     * 当前用户资料视图。
     */
    @Data
    public static class UserProfileView {
        /**
         * 用户头像
         */
        private String userAvatar;
        /**
         * 用户昵称（小程序 GET /me 不返回，仅管理端等使用）
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String userTitle;
        /**
         * 用户电话
         */
        private String userTel;
        /**
         * 用户生日
         */
        private String userBirth;
        /**
         * 是否已认证
         */
        private Boolean verified;
        /**
         * 风险等级
         */
        private String riskLevel;
        /**
         * 信用分
         */
        private Integer creditScore;
        /**
         * 城市
         */
        private String city;
        /**
         * 身份证后四位
         */
        private String idCardTail;
        /**
         * 实名认证姓名（已认证时返回，用于完善资料页展示）
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String realName;
        /**
         * 是否已被封禁（禁止下单），true 表示已封禁
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Boolean blocked;
    }

    /**
     * 商品列表项视图。
     */
    @Data
    public static class RentalGoodView {
        /**
         * 商品ID
         */
        private String goodId;
        /**
         * 规格ID
         */
        private String skuId;
        /**
         * 商品标题
         */
        private String goodTitle;
        /**
         * 商品副标题
         */
        private String goodSubTitle;
        /**
         * 最低日租金（单位：分），取该商品所有规格中租金最低的
         */
        private Integer dailyPrice;
        /**
         * 最低月租金（单位：分），按最低日租金×30 计算
         */
        private Integer monthlyPrice;
        /**
         * 押金
         */
        private Integer deposit;
        /**
         * 是否支持买断。
         */
        private Boolean buyoutEnabled;
        /**
         * 买断价，单位：分。
         */
        private Integer buyoutPrice;
        /**
         * 是否租完即送。
         */
        private Boolean rentToOwn;
        /**
         * 封面图
         */
        private String cover;
        /**
         * 商品标签
         */
        private String goodLabel;
        /**
         * 服务标签
         */
        private List<String> serviceTags = new ArrayList<>();
        /**
         * 库存
         */
        private Integer inventory;
        /** 有效订单的累计租赁件数。 */
        private Long sales;
        /**
         * 最小租期
         */
        private Integer minRent;
        /**
         * 是否支持分期。
         */
        private Boolean installmentEnabled;
        /**
         * 可选支付期数，包含 1 期；1 表示不分期。
         */
        private List<Integer> installmentPeriods = new ArrayList<>();
        /**
         * 配送信息。
         */
        private DeliveryInfoView deliveryInfo;
        /**
         * 地点
         */
        private String location;
        /**
         * 发货时效
         */
        private String deliveryLeadTime;
        /**
         * 摘要
         */
        private String summary;

        private String categoryCode;
        private String categoryName;
        private String parentCategoryCode;
        private String parentCategoryName;
        private String brand;
        private String deviceType;
        private List<String> supportedRentUnits = new ArrayList<>();
        private String defaultRentUnit;
        private Boolean featured;
        private Long featuredSort;

        @Data
        public static class DeliveryInfoView {
            /**
             * 是否支持快递。
             */
            private Boolean supportExpress;
            /**
             * 是否支持自提。
             */
            private Boolean supportStorePickup;
        }
    }

    /** 公开分类树节点。children 始终返回数组；不请求计数时 goodsCount 固定为 null。 */
    @Data
    public static class CatalogCategoryView {
        private String code;
        private String parentCode;
        private String name;
        private String shortName;
        private String description;
        private String coverImage;
        private String icon;
        private Integer sortOrder;
        private Long goodsCount;
        private String createdAt;
        private String updatedAt;
        private List<CatalogCategoryView> children = new ArrayList<>();
    }

    /**
     * 订单视图。
     */
    @Data
    public static class RentalOrderView {
        /**
         * 订单ID
         */
        private String orderId;
        /**
         * 租赁订单ID
         */
        private String rentOrderId;
        /**
         * 来源ID
         */
        private String sourceId;
        /**
         * 商品ID
         */
        private String goodId;
        /**
         * 商品标题
         */
        private String goodTitle;
        /**
         * 封面图
         */
        private String cover;
        /**
         * 支付宝状态
         */
        private String alipayStatus;
        /**
         * 租期
         */
        private Integer orderKeep;
        /**
         * 租期单位
         */
        private String orderRentUnit;
        /**
         * 订单押金
         */
        private Integer orderDeposit;
        /**
         * 首期金额
         */
        private Integer orderFirstAmount;
        /**
         * 每期金额
         */
        private Integer orderEveryAmount;
        /**
         * 尾期金额
         */
        private Integer orderEndAmount;
        /**
         * 当前期数
         */
        private Integer currentPeriod;
        /**
         * 总期数
         */
        private Integer totalPeriods;
        /**
         * 订单总额
         */
        private Integer orderTotal;
        /**
         * 订单交易类型
         */
        private Integer orderTradeType;
        /**
         * 是否支持买断。
         */
        private Boolean buyoutEnabled;
        /**
         * 买断价，单位：分。
         */
        private Integer buyoutPrice;
        /**
         * 买断是否已支付完成。
         */
        private Boolean buyoutPaid;
        /**
         * 是否租完即送。
         */
        private Boolean rentToOwn;
        /**
         * 创建时间
         */
        private String createdAt;
        /**
         * 发货时间
         */
        private String deliverAt;
        /**
         * 租赁开始时间
         */
        private String rentStartAt;
        /**
         * 租赁结束时间
         */
        private String rentEndAt;
        /**
         * 取货方式
         */
        private String pickupType;
        /**
         * 地址信息
         */
        private AddressView addressInfo;
        /**
         * 商家名称
         */
        private String merchantName;
        /**
         * 备注
         */
        private String note;
        /**
         * 头像
         */
        private String avatar;
        /**
         * em
         */
        private String em;
        /**
         * 快递单号
         */
        private String courierNo;
        /**
         * 快递公司名称
         */
        private String courierName;
        /**
         * 快递公司代码
         */
        private String courierCode;
        /**
         * 是否要求支付前上传身份证照片
         */
        private Boolean idCardPhotoRequired;
        /**
         * 是否要求确认收货前完成 e签宝电子合同签署
         */
        private Boolean esignRequired;
        /**
         * 已上传的身份证照片
         */
        private List<String> idCardPhotoUrls = new ArrayList<>();
        /**
         * 身份证照片上传时间
         */
        private Long idCardPhotoUploadedAt;
        /**
         * 身份证照片审核状态
         */
        private String idCardPhotoReviewStatus;
        /**
         * 身份证照片审核备注
         */
        private String idCardPhotoReviewRemark;
        /**
         * 身份证照片审核时间
         */
        private Long idCardPhotoReviewedAt;
        /**
         * 租赁协议编号。
         */
        private String contractNo;
        /**
         * 租赁协议 PDF 完整访问地址。
         */
        private String contractPdfUrl;
        /**
         * 租赁协议 PDF OSS 相对路径。
         */
        private String contractPdfPath;
        /**
         * 用户确认协议时间。
         */
        private String contractAgreedAt;
    }

    /**
     * 租赁协议预览视图。
     */
    @Data
    public static class RentalContractView {
        private String contractNo;
        private String contractVersion;
        private String contractTitle;
        private String contractSubtitle;
        private String orderId;
        private String orderNo;
        private String rentOrderId;
        private String signedAt;
        private String merchantName;
        private String merchantCreditCode;
        private String merchantLegalRepresentative;
        private String merchantRegisteredAddress;
        private String merchantAddress;
        private String buyerName;
        private String buyerIdCard;
        private String buyerPhone;
        private String buyerAddress;
        private String goodTitle;
        private String skuTitle;
        private String sourceId;
        private String authNo;
        private Integer quantity;
        private Integer orderKeep;
        private String orderRentUnit;
        private String rentStartAt;
        private String rentEndAt;
        private String depositText;
        private String rentText;
        private String totalText;
        private String contractPdfUrl;
        private String contractPdfPath;
        private List<ContractSectionView> sections = new ArrayList<>();
    }

    @Data
    public static class ContractSectionView {
        private String title;
        private List<String> items = new ArrayList<>();
    }

    /**
     * 订单状态统计视图。
     * pending: 待处理 (CREATED, SIGNED, APPROVED, PAID, PENDING_CANCEL)
     * renting: 租赁中 (DELIVERED, RECEIVED, RETURN_DELIVERED)
     * finished: 已完成 (RETURN_RECEIVED, FINISHED, CLOSED)
     */
    @Data
    public static class OrderStatsView {
        /**
         * 全部订单数量
         */
        private long total;
        /**
         * 待处理数量
         */
        private long pending;
        /**
         * 租赁中数量
         */
        private long renting;
        /**
         * 已完成数量
         */
        private long finished;
    }

    /**
     * 登录结果视图。
     */
    @Data
    public static class LoginDataView {
        /**
         * Token
         */
        private String token;
        /**
         * 刷新Token
         */
        private String refreshToken;
        /**
         * 过期时间
         */
        private String expiredAt;
        /**
         * 用户ID
         */
        private String userId;
        /**
         * 用户资料
         */
        private UserProfileView user;
    }

    /**
     * 交易组件预览结果。
     */
    @Data
    public static class TradePreviewView {
        /**
         * 库存
         */
        private Integer inventory;
        /**
         * 是否允许
         */
        private Boolean allowed;
        /**
         * 业务参数数据
         */
        private BizParamDataView bizParamData;

        @Data
        public static class BizParamDataView {
            /**
             * 商品信息
             */
            private ItemInfoView itemInfo;
            /**
             * 费用信息
             */
            private CostInfoView costInfo;
            /**
             * 配送信息
             */
            private DeliveryInfoView deliveryInfo;
            /**
             * 风险信息
             */
            private RiskInfoView riskInfo;
        }

        @Data
        public static class ItemInfoView {
            /**
             * 外部商品ID
             */
            private String outItemId;
            /**
             * 外部规格ID
             */
            private String outSkuId;
            /**
             * 标题
             */
            private String title;
        }

        @Data
        public static class CostInfoView {
            /**
             * 总租金
             */
            private String totalRent;
            /**
             * 原价
             */
            private String originalPrice;
            /**
             * 押金
             */
            private String deposit;
            /**
             * 运费
             */
            private String freight;
            /**
             * 分期计划
             */
            private StagePayPlanView stagePayPlan;
        }

        @Data
        public static class StagePayPlanView {
            /**
             * 分期计划信息列表
             */
            private List<StagePayPlanInfoView> stagePayPlanInfos = new ArrayList<>();
        }

        @Data
        public static class StagePayPlanInfoView {
            /**
             * 期数
             */
            private Integer period;
            /**
             * 计划支付金额
             */
            private String planPayPrice;
            /**
             * 原价
             */
            private String originalPrice;
        }

        @Data
        public static class DeliveryInfoView {
            /**
             * 是否支持快递
             */
            private Boolean supportExpress;
            /**
             * 是否支持自提
             */
            private Boolean supportStorePickup;
        }

        @Data
        public static class RiskInfoView {
            /**
             * 信用分
             */
            private Integer creditScore;
            /**
             * 风险等级
             */
            private String riskLevel;
        }
    }

    /**
     * 交易组件建单结果。
     */
    @Data
    public static class TradeOrderCreateView {
        /**
         * 订单ID
         */
        private String orderId;
        /**
         * 租赁订单ID
         */
        private String rentOrderId;
        /**
         * 来源ID
         */
        private String sourceId;
        /**
         * 支付宝状态
         */
        private String alipayStatus;
        /**
         * 订单信息
         */
        private RentalOrderView order;
    }

    /**
     * 实名认证结果。
     */
    @Data
    public static class RealNameVerifyView {
        /**
         * 是否已认证
         */
        private Boolean verified;
        /**
         * 用户资料
         */
        private UserProfileView user;
        /**
         * 订单信息
         */
        private RentalOrderView order;
    }

    /**
     * 支付宝回调受理结果。
     */
    @Data
    public static class CallbackAckView {
        /**
         * 是否接受
         */
        private Boolean accepted;
    }

    /**
     * 后台发起支付结果。
     */
    @Data
    public static class AdminPaymentView {
        /**
         * 交易号
         */
        private String tradeNo;
        /**
         * 订单信息
         */
        private RentalOrderView order;
    }

    /**
     * 后台主动查询支付宝订单状态结果。
     */
    @Data
    public static class AdminAlipayStatusView {
        /**
         * 是否成功
         */
        private Boolean success;
        /**
         * 支付宝状态
         */
        private String alipayStatus;
        /**
         * 子状态码
         */
        private String subCode;
        /**
         * 子消息
         */
        private String subMsg;
        /**
         * 订单信息
         */
        private RentalOrderView order;
    }

    /**
     * 后台风控查询结果。
     * 保留 raw 字段，方便排查支付宝侧的原始返回。
     */
    @Data
    public static class AdminRiskView {
        /**
         * 是否成功
         */
        private Boolean success;
        /**
         * 子状态码
         */
        private String subCode;
        /**
         * 子消息
         */
        private String subMsg;
        /**
         * 原始数据
         */
        private Map<String, Object> raw;
    }
}

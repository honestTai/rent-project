package com.fly.rent.config;

import com.common.zhongtai.config.ZhongtaiConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 支付宝租赁中台配置读取服务。
 * <p>
 * 将支付宝交易组件、芝麻信用、商品同步等原硬编码参数集中到中台配置表，业务代码只按语义方法读取。
 * </p>
 */
@Service
public class AlipayPlatformConfigService {

    private static final String SYSTEM_CODE = "alipay";
    private static final String ITEM_FINENESS_WHOLE_NEW = "wholeNew";
    private static final String ITEM_FINENESS_SECOND_HAND = "secondHand";
    private static final String DEFAULT_ITEM_FINENESS_GRADE = "95new";
    private static final List<String> ITEM_FINENESS_GRADES =
            Collections.unmodifiableList(Arrays.asList("99new", "95new", "90new", "80new", "70new"));

    private final ZhongtaiConfigService configService;

    /**
     * 创建支付宝中台配置读取服务。
     *
     * @param configService 中台配置读取服务
     */
    public AlipayPlatformConfigService(ZhongtaiConfigService configService) {
        this.configService = configService;
    }

    /**
     * 获取芝麻信用服务 ID。
     *
     * @return 芝麻信用服务 ID
     */
    public String zmServiceId() {
        return get("alipay.zm-service-id");
    }

    /**
     * 获取支付宝租赁类目 ID。
     *
     * @return 租赁类目 ID
     */
    public String rentCategoryId() {
        return get("alipay.rent.category-id");
    }

    /**
     * 获取支付宝收款账号 userId。
     *
     * @return 收款账号 userId
     */
    public String payeeUserId() {
        return get("alipay.payee-user-id");
    }

    /**
     * 获取交易组件 AppId。
     *
     * @return 交易组件 AppId
     */
    public String tradeAppId() {
        return get("alipay.trade-app-id");
    }

    /**
     * 获取租赁协议页面路径。
     *
     * @return 协议路径
     */
    public String protocolPath() {
        return get("alipay.protocol.path");
    }

    /**
     * 获取租赁协议名称。
     *
     * @return 协议名称
     */
    public String protocolName() {
        return get("alipay.protocol.name");
    }

    /**
     * 是否启用支付宝接口 Demo 阻断模式。
     * 开启后服务端不会向支付宝开放平台发起请求，只返回带明确标识的模拟响应。
     */
    public boolean demoModeEnabled() {
        return Boolean.parseBoolean(get("alipay.demo-mode.enabled"));
    }

    /**
     * 获取风险咨询协议 URL。
     *
     * @return 风险咨询协议 URL
     */
    public String riskProtocolUrl() {
        return get("alipay.risk.protocol-url");
    }

    /**
     * 获取风险咨询协议名称。
     *
     * @return 风险咨询协议名称
     */
    public String riskProtocolName() {
        return get("alipay.risk.protocol-name");
    }

    /**
     * 获取商品成色编码。
     *
     * @return 商品成色编码
     */
    public String itemFineness() {
        return itemFineness(null);
    }

    /**
     * 优先使用商品自带成色，未配置时回退到中台默认成色。
     *
     * @param itemFineness 商品成色
     * @return 支付宝允许的成色编码
     */
    public String itemFineness(String itemFineness) {
        if (StringUtils.hasText(itemFineness)) {
            return normalizeItemFineness(itemFineness);
        }
        return normalizeItemFineness(get("alipay.item.fineness"));
    }

    /**
     * 获取商品成色等级。
     *
     * @return 商品成色等级
     */
    public String itemFinenessGrade() {
        return normalizeItemFinenessGrade(get("alipay.item.fineness-grade"));
    }

    /**
     * 优先使用商品自带成色等级，未配置时回退到中台默认成色等级。
     *
     * @param itemFinenessGrade 商品成色等级
     * @return 成色等级
     */
    public String itemFinenessGrade(String itemFinenessGrade) {
        if (StringUtils.hasText(itemFinenessGrade)) {
            return normalizeItemFinenessGrade(itemFinenessGrade);
        }
        return itemFinenessGrade();
    }

    /**
     * 获取租赁模式编码。
     *
     * @return 租赁模式编码
     */
    public String rentModel() {
        return get("alipay.rent-model");
    }

    /**
     * 获取归还详细地址。
     *
     * @return 归还详细地址
     */
    public String returnAddressDetail() {
        return get("rent.return.address.detail");
    }

    /**
     * 获取归还联系人。
     *
     * @return 归还联系人
     */
    public String returnConsignee() {
        return get("rent.return.consignee");
    }

    /**
     * 获取归还联系电话。
     *
     * @return 归还联系电话
     */
    public String returnMobile() {
        return get("rent.return.mobile");
    }

    /**
     * 获取客服电话。
     *
     * @return 客服电话
     */
    public String servicePhone() {
        return get("rent.service-phone");
    }

    /**
     * 获取公开图片访问前缀。
     *
     * @return 图片访问前缀，例如 https://example.com/uploads
     */
    public String publicImageBaseUrl() {
        String storageType = imageStorageType();
        if ("local".equals(storageType)) {
            return localImageBaseUrl();
        }
        return aliyunImageBaseUrl();
    }

    /**
     * 获取当前图片上传方式。
     *
     * @return aliyun 或 local
     */
    public String imageStorageType() {
        String value = configService.getOptionalString("common", "oss.storage-type");
        if (value == null || value.trim().isEmpty()) {
            return "aliyun";
        }
        String normalized = value.trim().toLowerCase();
        return "local".equals(normalized) ? "local" : "aliyun";
    }

    /**
     * 获取阿里云 OSS 回显地址。
     *
     * @return 阿里云 OSS 或 CDN 图片前缀
     */
    public String aliyunImageBaseUrl() {
        String value = configService.getOptionalString("common", "oss.aliyun-public-base-url");
        if (value != null && !value.trim().isEmpty()) {
            return normalizeBaseUrl(value, "");
        }
        String bucketName = configService.getOptionalString("common", "oss.bucket-name");
        String endpoint = configService.getOptionalString("common", "oss.endpoint");
        if (bucketName != null && !bucketName.trim().isEmpty()
                && endpoint != null && !endpoint.trim().isEmpty()) {
            return "https://" + bucketName.trim() + "." + endpoint.trim() + "/uploads";
        }
        return "";
    }

    /**
     * 获取本地图片回显地址。
     *
     * @return 本地域名图片前缀
     */
    public String localImageBaseUrl() {
        String value = configService.getOptionalString("common", "oss.local-public-base-url");
        return normalizeBaseUrl(value, "/uploads");
    }

    /**
     * 获取商品同步图片基础地址。
     *
     * @return 图片基础地址
     */
    public String goodsSyncAssetBaseUrl() {
        return get("alipay.goods.sync.asset-base-url");
    }

    /**
     * 获取商品同步类目 ID。
     *
     * @return 支付宝商品类目 ID
     */
    public String goodsSyncCategoryId() {
        return get("alipay.goods.sync.category-id");
    }

    /**
     * 获取商品同步业务模式。
     *
     * @return 业务模式
     */
    public String goodsSyncBusinessModel() {
        return get("alipay.goods.sync.business-model");
    }

    /**
     * 获取商品详情页模式。
     *
     * @return 详情页模式
     */
    public String goodsSyncItemDetailsPageModel() {
        return get("alipay.goods.sync.item-details-page-model");
    }

    /**
     * 获取商品详情页路径模板。
     *
     * @return 路径模板
     */
    public String goodsSyncPathTemplate() {
        return get("alipay.goods.sync.path-template");
    }

    /**
     * 获取租赁协议标题。
     *
     * @return 协议标题
     */
    public String contractTitle() {
        return get("rent.contract.title");
    }

    /**
     * 获取租赁协议副标题。
     *
     * @return 协议副标题
     */
    public String contractSubtitle() {
        return get("rent.contract.subtitle");
    }

    /**
     * 获取租赁协议出租方名称。
     *
     * @return 出租方名称
     */
    public String contractMerchantName() {
        return get("rent.contract.merchant.name");
    }

    /**
     * 获取租赁协议出租方统一社会信用代码。
     *
     * @return 统一社会信用代码
     */
    public String contractMerchantCreditCode() {
        return get("rent.contract.merchant.credit-code");
    }

    /**
     * 获取租赁协议出租方法定代表人。
     *
     * @return 法定代表人
     */
    public String contractMerchantLegalRepresentative() {
        return get("rent.contract.merchant.legal-representative");
    }

    /**
     * 获取租赁协议出租方注册地址。
     *
     * @return 注册地址
     */
    public String contractMerchantRegisteredAddress() {
        return get("rent.contract.merchant.registered-address");
    }

    /**
     * 获取租赁协议出租方发货/联系地址。
     *
     * @return 发货/联系地址
     */
    public String contractMerchantAddress() {
        return get("rent.contract.merchant.address");
    }

    /**
     * 获取租赁协议条款 JSON。
     *
     * @return 协议条款 JSON
     */
    public String contractSectionsJson() {
        return get("rent.contract.sections.json");
    }

    /**
     * 获取小程序订单详情路径模板。
     *
     * @return 订单详情路径模板，可包含 {orderId} 或 ${orderId}
     */
    public String orderDetailPathTemplate() {
        return get("miniapp.order.detail-path-template");
    }

    /**
     * 获取支付宝支付超时表达式。
     *
     * @return 支付宝 pay_timeout_express，例如 30m
     */
    public String payTimeoutExpress() {
        return get("rent.pay.timeout-express");
    }

    /**
     * 获取支付锁超时时间。
     *
     * @return 分钟
     */
    public int payLockTimeoutMinutes() {
        return getInt("rent.pay.lock-timeout-minutes");
    }

    /**
     * 获取下单锁超时时间。
     *
     * @return 秒
     */
    public int createLockTimeoutSeconds() {
        return getInt("rent.order.create-lock-timeout-seconds");
    }

    /**
     * 获取下单限流窗口。
     *
     * @return 分钟
     */
    public int createLimitWindowMinutes() {
        return getInt("rent.order.create-limit-window-minutes");
    }

    /**
     * 获取下单限流次数。
     *
     * @return 次数
     */
    public int createLimitMaxTimes() {
        return getInt("rent.order.create-limit-max-times");
    }

    /**
     * 获取未支付订单超时时间。
     *
     * @return 分钟
     */
    public int unpaidOrderTimeoutMinutes() {
        return getInt("rent.order.unpaid-timeout-minutes");
    }

    /**
     * 获取允许存在的未支付订单数量。
     *
     * @return 数量
     */
    public int unpaidOrderMaxCount() {
        return getInt("rent.order.unpaid-max-count");
    }

    /**
     * 获取售后同步默认扫描范围。
     *
     * @return 天数
     */
    public int aftersaleScanRangeDays() {
        return getInt("rent.aftersale.scan-range-days");
    }

    private String get(String key) {
        return configService.getString(SYSTEM_CODE, key);
    }

    private int getInt(String key) {
        return configService.getInt(SYSTEM_CODE, key);
    }

    private String normalizeItemFineness(String value) {
        if (!StringUtils.hasText(value)) {
            return ITEM_FINENESS_SECOND_HAND;
        }
        String trimmed = value.trim();
        if (ITEM_FINENESS_WHOLE_NEW.equals(trimmed) || ITEM_FINENESS_SECOND_HAND.equals(trimmed)) {
            return trimmed;
        }
        return ITEM_FINENESS_SECOND_HAND;
    }

    private String normalizeItemFinenessGrade(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_ITEM_FINENESS_GRADE;
        }
        String trimmed = value.trim();
        return ITEM_FINENESS_GRADES.contains(trimmed) ? trimmed : DEFAULT_ITEM_FINENESS_GRADE;
    }

    private String normalizeBaseUrl(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

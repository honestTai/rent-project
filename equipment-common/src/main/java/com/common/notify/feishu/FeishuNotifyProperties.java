package com.common.notify.feishu;

import com.common.zhongtai.config.ZhongtaiConfigService;
import com.common.zhongtai.config.ZhongtaiNotifyChannelService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 飞书通知配置。
 * 统一沉淀应用凭证、接收群、报表链接和支付宝订单跳转链接。
 */
@Data
@ConfigurationProperties(prefix = "notify.feishu")
public class FeishuNotifyProperties {

    private transient ZhongtaiConfigService zhongtaiConfigService;
    private transient ZhongtaiNotifyChannelService zhongtaiNotifyChannelService;

    /** 全局通知开关，关闭时所有发送行为直接降级为 no-op。 */
    private boolean enabled = false;

    /** 飞书开放平台应用 appId。 */
    private String appId = "";

    /** 飞书开放平台应用 appSecret。 */
    private String appSecret = "";

    /** 接收方类型。 */
    private String receiveIdType = "";

    /** 统一接收群 ID。 */
    private String chatId = "";

    /** 飞书开放平台基础地址。 */
    private String baseUrl = "";

    /** 支付宝租赁报表中心跳转链接。 */
    private ReportLinks reportLinks = new ReportLinks();

    /** 支付宝租赁订单相关跳转链接。 */
    private AlipayLinks alipayLinks = new AlipayLinks();

    /**
     * 注入中台配置读取服务。
     *
     * @param zhongtaiConfigService 中台配置读取服务
     */
    @Autowired
    public void setZhongtaiConfigService(ZhongtaiConfigService zhongtaiConfigService) {
        this.zhongtaiConfigService = zhongtaiConfigService;
    }

    /**
     * 注入中台通知通道读取服务。
     *
     * @param zhongtaiNotifyChannelService 中台通知通道读取服务
     */
    @Autowired(required = false)
    public void setZhongtaiNotifyChannelService(ZhongtaiNotifyChannelService zhongtaiNotifyChannelService) {
        this.zhongtaiNotifyChannelService = zhongtaiNotifyChannelService;
    }

    public boolean isAppConfigured() {
        return hasText(getAppId()) && hasText(getAppSecret());
    }

    public boolean hasReceiver() {
        return hasText(getChatId()) && hasText(getReceiveIdType());
    }

    public boolean isReady() {
        return isEnabled() && isAppConfigured() && hasReceiver();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public boolean isEnabled() {
        return zhongtaiConfigService.getBoolean("common", "notify.feishu.enabled");
    }

    public String getAppId() {
        return firstText(defaultChannel().getAppId(), getConfig("notify.feishu.app-id"));
    }

    public String getAppSecret() {
        return firstText(defaultChannel().getAppSecret(), getConfig("notify.feishu.app-secret"));
    }

    public String getReceiveIdType() {
        return firstText(defaultChannel().getReceiveIdType(), getConfig("notify.feishu.receive-id-type"));
    }

    public String getChatId() {
        return firstText(defaultChannel().getReceiveId(), getConfig("notify.feishu.chat-id"));
    }

    public String getBaseUrl() {
        return getConfig("notify.feishu.base-url");
    }

    public ReportLinks getReportLinks() {
        ReportLinks dynamicLinks = new ReportLinks();
        dynamicLinks.setAlipay(getConfig("notify.feishu.report-links.alipay"));
        return dynamicLinks;
    }

    public AlipayLinks getAlipayLinks() {
        AlipayLinks dynamicLinks = new AlipayLinks();
        dynamicLinks.setOrderDetailTemplate(getConfig("notify.feishu.alipay-links.order-detail-template"));
        dynamicLinks.setOrderList(getConfig("notify.feishu.alipay-links.order-list"));
        return dynamicLinks;
    }

    private String getConfig(String configKey) {
        return zhongtaiConfigService.getString("common", configKey);
    }

    private ZhongtaiNotifyChannelService.ChannelSnapshot defaultChannel() {
        if (zhongtaiNotifyChannelService == null) {
            return new ZhongtaiNotifyChannelService.ChannelSnapshot();
        }
        try {
            return zhongtaiNotifyChannelService.defaultFeishuChannel();
        } catch (Exception e) {
            // 默认通道读取失败不影响业务，返回空快照由上层 isReady 判定跳过通知。
            return new ZhongtaiNotifyChannelService.ChannelSnapshot();
        }
    }

    private String firstText(String primary, String secondary) {
        return StringUtils.hasText(primary) ? primary : secondary;
    }

    @Data
    public static class ReportLinks {
        /** 支付宝租赁报表中心链接。 */
        private String alipay = "";
    }

    @Data
    public static class AlipayLinks {
        /** 订单详情模板，允许使用 {orderId} 占位。 */
        private String orderDetailTemplate = "";
        /** 订单列表入口链接。 */
        private String orderList = "";
    }
}

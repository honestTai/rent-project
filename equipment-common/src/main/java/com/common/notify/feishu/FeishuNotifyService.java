package com.common.notify.feishu;

import com.common.zhongtai.config.ZhongtaiNotifyChannelService;
import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 飞书通知门面。
 * 上层业务只需传业务语义字段，不直接关心卡片 JSON、token 获取和消息接口细节。
 */
@Slf4j
public class FeishuNotifyService {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String REPORT_SCENE_CODE = "report_created";
    private static final String ALIPAY_SYSTEM_CODE = "alipay";
    private static final String COMMON_SYSTEM_CODE = "common";

    private final FeishuNotifyProperties properties;
    private final FeishuCardBuilder cardBuilder;
    private final FeishuMessageClient messageClient;
    private final Executor feishuNotifyExecutor;
    private final ZhongtaiNotifyChannelService notifyChannelService;

    public FeishuNotifyService(FeishuNotifyProperties properties,
                               FeishuCardBuilder cardBuilder,
                               FeishuMessageClient messageClient,
                               Executor feishuNotifyExecutor) {
        this(properties, cardBuilder, messageClient, feishuNotifyExecutor, null);
    }

    /**
     * 创建飞书通知门面。
     * <p>
     * notifyChannelService 负责按中台场景通道读取飞书机器人和接收群。
     * </p>
     */
    public FeishuNotifyService(FeishuNotifyProperties properties,
                               FeishuCardBuilder cardBuilder,
                               FeishuMessageClient messageClient,
                               Executor feishuNotifyExecutor,
                               ZhongtaiNotifyChannelService notifyChannelService) {
        this.properties = properties;
        this.cardBuilder = cardBuilder;
        this.messageClient = messageClient;
        this.feishuNotifyExecutor = feishuNotifyExecutor;
        this.notifyChannelService = notifyChannelService;
    }

    /**
     * 发送周期报表通知卡片。
     * 只负责把通知任务投递到独立线程池，避免报表生成链路被飞书接口耗时拖慢。
     */
    public void sendPeriodicReportCard(String systemName,
                                       String reportType,
                                       String periodKey,
                                       String periodRange,
                                       String generateMode,
                                       Date generatedAt,
                                       Map<String, String> metricFields,
                                       String detailUrl) {
        sendPeriodicReportCard(resolveReportSystemCodeByName(systemName), systemName, reportType, periodKey,
                periodRange, generateMode, generatedAt, metricFields, detailUrl);
    }

    /**
     * 发送指定系统的周期报表通知卡片。
     * systemCode 直接对应中台通知通道的 system_code，保证业务通知走独立机器人。
     */
    public void sendPeriodicReportCard(String systemCode,
                                       String systemName,
                                       String reportType,
                                       String periodKey,
                                       String periodRange,
                                       String generateMode,
                                       Date generatedAt,
                                       Map<String, String> metricFields,
                                       String detailUrl) {
        ZhongtaiNotifyChannelService.ChannelSnapshot channel = getReportChannel(systemCode);
        if (!isReady(channel)) {
            return;
        }
        Map<String, String> metricFieldSnapshot = metricFields == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(metricFields);
        Date generatedAtSnapshot = generatedAt == null ? null : new Date(generatedAt.getTime());
        dispatchAsync("周期报表通知", () -> {
            Map<String, String> basicFields = new LinkedHashMap<>();
            basicFields.put("系统", safe(systemName));
            basicFields.put("报表类型", safe(reportType));
            basicFields.put("周期", safe(periodRange));
            basicFields.put("生成方式", safe(generateMode));
            basicFields.put("周期键", safe(periodKey));
            basicFields.put("生成时间", formatDate(generatedAtSnapshot));
            String content = cardBuilder.buildPeriodicReportCard(
                    "[" + safe(systemName) + "][" + safe(reportType) + "] 已生成",
                    basicFields,
                    metricFieldSnapshot,
                    "查看报表中心",
                    detailUrl
            );
            sendByChannel(channel, content,
                    "report-" + safe(systemName) + "-" + safe(periodKey) + "-" + safe(generateMode));
        });
    }

    /**
     * 发送支付宝租赁订单生命周期通知卡片。
     * 生命周期通知改为异步派发，确保建单、回调、审核、发货等主流程不等待飞书网络请求。
     */
    public void sendAlipayOrderLifecycleCard(String eventName,
                                             String orderNo,
                                             String goodsTitle,
                                             String userLabel,
                                             String statusLabel,
                                             String amountSummary,
                                             String logisticsNo,
                                             Date eventTime,
                                             String source,
                                             String detailUrl,
                                             String listUrl,
                                             String uuidSuffix) {
        ZhongtaiNotifyChannelService.ChannelSnapshot channel = getReportChannel(ALIPAY_SYSTEM_CODE);
        if (!isReady(channel)) {
            return;
        }
        Date eventTimeSnapshot = eventTime == null ? null : new Date(eventTime.getTime());
        dispatchAsync("支付宝租赁订单生命周期通知", () -> {
            Map<String, String> basicFields = new LinkedHashMap<>();
            basicFields.put("事件", safe(eventName));
            basicFields.put("订单号", safe(orderNo));
            basicFields.put("商品", safe(goodsTitle));
            basicFields.put("用户", safe(userLabel));
            basicFields.put("当前状态", safe(statusLabel));
            basicFields.put("事件时间", formatDate(eventTimeSnapshot));

            Map<String, String> metricFields = new LinkedHashMap<>();
            metricFields.put("金额摘要", safe(amountSummary));
            metricFields.put("物流单号", safe(logisticsNo));
            metricFields.put("来源", safe(source));

            String content = cardBuilder.buildLifecycleCard(
                    "[支付宝租赁] 订单状态更新",
                    basicFields,
                    metricFields,
                    "查看订单详情",
                    detailUrl,
                    "查看订单列表",
                    listUrl
            );
            sendByChannel(channel, content,
                    "alipay-order-" + safe(uuidSuffix));
        });
    }

    /**
     * 发送异常类卡片。
     * 异常提醒也不反向影响主流程；即使飞书不可用，也只保留本地日志。
     */
    public void sendExceptionCard(String title,
                                  String scene,
                                  String summary,
                                  String detail,
                                  String actionText,
                                  String actionUrl,
                                  String uuidSuffix) {
        sendExceptionCard(resolveExceptionSystemCode(scene), title, scene, summary, detail, actionText, actionUrl, uuidSuffix);
    }

    /**
     * 按指定系统发送异常类卡片。
     * 支付宝订单通知复用本系统的报表机器人，避免同系统拆出多套飞书机器人。
     */
    public void sendExceptionCard(String systemCode,
                                  String title,
                                  String scene,
                                  String summary,
                                  String detail,
                                  String actionText,
                                  String actionUrl,
                                  String uuidSuffix) {
        ZhongtaiNotifyChannelService.ChannelSnapshot channel = getNotifyChannel(systemCode);
        if (!isReady(channel)) {
            return;
        }
        dispatchAsync("异常提醒通知", () -> {
            Map<String, String> basicFields = new LinkedHashMap<>();
            basicFields.put("场景", safe(scene));
            basicFields.put("摘要", safe(summary));
            basicFields.put("详情", safe(detail));
            basicFields.put("时间", formatDate(new Date()));
            String content = cardBuilder.buildExceptionCard(title, basicFields, actionText, actionUrl);
            sendByChannel(channel, content,
                    "exception-" + safe(uuidSuffix));
        });
    }

    private boolean isReady(ZhongtaiNotifyChannelService.ChannelSnapshot channel) {
        if (channel == null) {
            return false;
        }
        if (!properties.isEnabled()) {
            return false;
        }
        if (hasText(resolveAppId(channel)) && hasText(resolveAppSecret(channel))
                && hasText(resolveReceiveIdType(channel)) && hasText(resolveReceiveId(channel))) {
            return true;
        }
        return false;
    }

    /**
     * 异步提交飞书任务。
     * 提交失败或执行异常都只记日志，明确不回抛到业务线程，保证通知能力永远是旁路能力。
     */
    private void dispatchAsync(String scene, Runnable task) {
        try {
            feishuNotifyExecutor.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.warn("异步发送飞书{}失败，已忽略，不影响主流程", scene, e);
                }
            });
        } catch (Exception e) {
            log.warn("提交飞书{}任务失败，已忽略，不影响主流程", scene, e);
        }
    }

    /**
     * 通过中台通知通道发送卡片。
     */
    private void sendByChannel(ZhongtaiNotifyChannelService.ChannelSnapshot channel, String content, String uuid) {
        messageClient.sendInteractiveCard(resolveReceiveId(channel), resolveReceiveIdType(channel),
                resolveAppId(channel), resolveAppSecret(channel), content, uuid);
    }

    private ZhongtaiNotifyChannelService.ChannelSnapshot getReportChannel(String systemCode) {
        String reportSystemCode = normalizeReportSystemCode(systemCode);
        String channelCode = COMMON_SYSTEM_CODE.equals(reportSystemCode)
                ? "feishu.report"
                : "feishu." + reportSystemCode + ".report";
        return getChannel(reportSystemCode, REPORT_SCENE_CODE, channelCode);
    }

    private String normalizeReportSystemCode(String systemCode) {
        String safeCode = safe(systemCode).toLowerCase();
        if (ALIPAY_SYSTEM_CODE.equals(safeCode)) {
            return safeCode;
        }
        return COMMON_SYSTEM_CODE;
    }

    private String resolveReportSystemCodeByName(String systemName) {
        String safeName = safe(systemName);
        if ("支付宝租赁".equals(safeName)) {
            return ALIPAY_SYSTEM_CODE;
        }
        return COMMON_SYSTEM_CODE;
    }

    private ZhongtaiNotifyChannelService.ChannelSnapshot getChannel(String systemCode, String sceneCode, String channelCode) {
        if (notifyChannelService == null) {
            log.debug("未配置中台通知通道读取服务，跳过飞书通知: systemCode={}, sceneCode={}, channelCode={}",
                    systemCode, sceneCode, channelCode);
            return new ZhongtaiNotifyChannelService.ChannelSnapshot();
        }
        try {
            return notifyChannelService.getChannel(systemCode, sceneCode, channelCode);
        } catch (Exception e) {
            // 通道读取异常只记日志，返回空快照，保证飞书通知永远是旁路能力。
            log.warn("读取飞书通知通道失败，已跳过通知: systemCode={}, sceneCode={}, channelCode={}",
                    systemCode, sceneCode, channelCode, e);
            return new ZhongtaiNotifyChannelService.ChannelSnapshot();
        }
    }

    private String resolveExceptionSystemCode(String scene) {
        return COMMON_SYSTEM_CODE;
    }

    private ZhongtaiNotifyChannelService.ChannelSnapshot getNotifyChannel(String systemCode) {
        String notifySystemCode = normalizeReportSystemCode(systemCode);
        if (COMMON_SYSTEM_CODE.equals(notifySystemCode)) {
            return getChannel(COMMON_SYSTEM_CODE, "default", "feishu.default");
        }
        return getReportChannel(notifySystemCode);
    }

    private String resolveAppId(ZhongtaiNotifyChannelService.ChannelSnapshot channel) {
        String channelValue = channel == null ? null : channel.getAppId();
        return hasText(channelValue) ? channelValue : properties.getAppId();
    }

    private String resolveAppSecret(ZhongtaiNotifyChannelService.ChannelSnapshot channel) {
        String channelValue = channel == null ? null : channel.getAppSecret();
        return hasText(channelValue) ? channelValue : properties.getAppSecret();
    }

    private String resolveReceiveIdType(ZhongtaiNotifyChannelService.ChannelSnapshot channel) {
        String channelValue = channel == null ? null : channel.getReceiveIdType();
        return hasText(channelValue) ? channelValue : properties.getReceiveIdType();
    }

    private String resolveReceiveId(ZhongtaiNotifyChannelService.ChannelSnapshot channel) {
        String channelValue = channel == null ? null : channel.getReceiveId();
        return hasText(channelValue) ? channelValue : properties.getChatId();
    }

    private String formatDate(Date date) {
        if (date == null) {
            return "-";
        }
        return new SimpleDateFormat(DATE_TIME_PATTERN).format(date);
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

package com.fly.rent.support.task;

import com.fly.rent.legacy.service.RentOrderSafetyService;
import com.fly.rent.miniapp.catalog.MiniappCatalogService;
import com.fly.rent.miniapp.order.RentContractAlipaySyncBatchService;
import com.fly.rent.report.service.PeriodicReportService;
import com.fly.rent.web.goods.AlipayGoodsSyncService;
import com.alipay.api.AlipayApiException;
import com.common.zhongtai.config.ZhongtaiScheduleTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * 定时任务入口：超时未支付订单关闭、小程序热点缓存定时刷新。
 *
 * @author HonestTat
 * @since 2026-03-11
 */
@Component
public class ScheduledTasks {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);

    @Autowired
    private RentOrderSafetyService rentOrderSafetyService;
    @Autowired
    private MiniappCatalogService miniappCatalogService;
    @Autowired
    private PeriodicReportService periodicReportService;
    @Autowired
    private ZhongtaiScheduleTaskService scheduleTaskService;
    @Autowired
    private AlipayGoodsSyncService alipayGoodsSyncService;
    @Autowired
    private RentContractAlipaySyncBatchService contractAlipaySyncBatchService;
    /**
     * 启动时输出一次日志，方便确认定时任务已经注册成功。
     */
    @PostConstruct
    public void init() {
        logger.info("项目启动完成，已启用超时未支付订单自动关闭、小程序热点缓存刷新、租赁合同回传补偿等定时任务");
    }

    /**
     * 每 5 分钟扫描一次超时未支付订单。
     */
    @Scheduled(fixedDelayString = "${zhongtai.schedule.poll-interval:30000}", initialDelayString = "${zhongtai.schedule.initial-delay:30000}")
    public void scheduledTask() {
        if (!scheduleTaskService.shouldRunNow("alipay", "alipay.order.close-expired")) {
            return;
        }
        try {
            rentOrderSafetyService.closeExpiredUnpaidOrders();
        } catch (AlipayApiException e) {
            logger.error("关闭超时未支付订单失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 每 5 分钟刷新小程序热点缓存：首页轮播，防雪崩并保证数据约 5 分钟更新一次。
     */
    @Scheduled(fixedDelayString = "${zhongtai.schedule.poll-interval:30000}", initialDelayString = "${zhongtai.schedule.initial-delay:30000}")
    public void refreshMiniappCache() {
        if (!scheduleTaskService.shouldRunNow("alipay", "alipay.cache.refresh")) {
            return;
        }
        try {
            miniappCatalogService.refreshBannersCache();
        } catch (Exception e) {
            logger.warn("刷新小程序热点缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 每 5 分钟回查审核中的支付宝商品状态，兜底处理商品状态通知未到达的情况。
     */
    @Scheduled(fixedDelayString = "${zhongtai.schedule.poll-interval:30000}", initialDelayString = "${zhongtai.schedule.initial-delay:30000}")
    public void refreshPendingAlipayItemStatuses() {
        if (!scheduleTaskService.shouldRunNow("alipay", "alipay.goods.status-refresh")) {
            return;
        }
        try {
            alipayGoodsSyncService.refreshPendingAlipayItemStatuses();
        } catch (Exception e) {
            logger.warn("回查支付宝商品审核状态失败: {}", e.getMessage());
        }
    }

    /**
     * 每晚 0 点补偿回传确认收货后仍未成功回传的租赁合同。
     * <p>
     * 触发时间和启停状态由中台定时任务配置控制；实际回传逐单记录订单台账，任务结束后发送飞书汇总。
     * </p>
     */
    @Scheduled(fixedDelayString = "${zhongtai.schedule.poll-interval:30000}", initialDelayString = "${zhongtai.schedule.initial-delay:30000}")
    public void syncReceivedOrderContracts() {
        if (!scheduleTaskService.shouldRunNow("alipay", "alipay.contract.sync")) {
            return;
        }
        try {
            contractAlipaySyncBatchService.syncUnsignedReceivedOrders();
        } catch (Exception e) {
            logger.warn("补偿回传租赁合同失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 每周一凌晨生成上一自然周报表。
     */
    @Scheduled(fixedDelayString = "${zhongtai.schedule.poll-interval:30000}", initialDelayString = "${zhongtai.schedule.initial-delay:30000}")
    public void generateWeeklyReport() {
        if (!scheduleTaskService.shouldRunNow("alipay", "alipay.report.weekly")) {
            return;
        }
        periodicReportService.generatePreviousWeekReport();
    }

    /**
     * 每月1号凌晨生成上一自然月报表。
     */
    @Scheduled(fixedDelayString = "${zhongtai.schedule.poll-interval:30000}", initialDelayString = "${zhongtai.schedule.initial-delay:30000}")
    public void generateMonthlyReport() {
        if (!scheduleTaskService.shouldRunNow("alipay", "alipay.report.monthly")) {
            return;
        }
        periodicReportService.generatePreviousMonthReport();
    }

    /**
     * 每年1月1日凌晨生成上一自然年报表。
     */
    @Scheduled(fixedDelayString = "${zhongtai.schedule.poll-interval:30000}", initialDelayString = "${zhongtai.schedule.initial-delay:30000}")
    public void generateYearlyReport() {
        if (!scheduleTaskService.shouldRunNow("alipay", "alipay.report.yearly")) {
            return;
        }
        periodicReportService.generatePreviousYearReport();
    }
}

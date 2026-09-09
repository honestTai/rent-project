package com.fly.rent.miniapp.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.common.notify.feishu.FeishuNotifyProperties;
import com.common.notify.feishu.FeishuNotifyService;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租赁合同回传支付宝的批量补偿服务。
 * <p>
 * 定时任务只负责按中台配置触发，本服务负责扫描未成功回传的已确认收货订单、
 * 逐单调用原有回传链路，并在执行结束后把汇总结果发送到飞书。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentContractAlipaySyncBatchService {

    private static final List<String> AFTER_RECEIVE_STATUSES = Arrays.asList(
            AlipayRentConstants.STATUS_RECEIVED,
            AlipayRentConstants.STATUS_RETURN_DELIVERED,
            AlipayRentConstants.STATUS_RETURN_RECEIVED,
            AlipayRentConstants.STATUS_FINISHED
    );
    private static final int MAX_ORDER_NO_PREVIEW = 10;

    private final OrderMapper orderMapper;
    private final RentContractAlipaySyncService contractAlipaySyncService;
    private final FeishuNotifyService feishuNotifyService;
    private final FeishuNotifyProperties feishuNotifyProperties;

    /**
     * 执行确认收货后未回传合同的批量补偿。
     *
     * @return 本次执行汇总，用于测试和后续扩展人工触发入口
     */
    public BatchResult syncUnsignedReceivedOrders() {
        BatchResult result = new BatchResult();
        result.startedAt = new Date();
        List<Order> candidates = findPendingOrders();
        result.scannedCount = candidates.size();
        log.info("开始批量补偿租赁合同回传支付宝: count={}", candidates.size());
        try {
            for (Order order : candidates) {
                syncOne(order, result);
            }
            return result;
        } finally {
            result.finishedAt = new Date();
            result.durationMillis = Math.max(0L, result.finishedAt.getTime() - result.startedAt.getTime());
            sendSummary(result);
            log.info("批量补偿租赁合同回传支付宝完成: scanned={}, processed={}, success={}, failed={}, duration={}ms",
                    result.scannedCount, result.processedCount, result.successCount, result.failedCount,
                    result.durationMillis);
        }
    }

    /**
     * 查询今年已越过用户确认收货节点、但合同尚未成功回传支付宝的订单。
     * <p>
     * 边界说明：只包含 RECEIVED 及之后仍属于履约链路的状态，不包含 CREATED/SIGNED/PAID/DELIVERED/CLOSED。
     * </p>
     */
    private List<Order> findPendingOrders() {
        return orderMapper.selectList(new QueryWrapper<Order>()
                .in("alipay_status", AFTER_RECEIVE_STATUSES)
                .ge("created_at", currentYearStartMillis())
                .and(wrapper -> wrapper
                        .isNull("contract_alipay_sync_status")
                        .or().ne("contract_alipay_sync_status", RentContractAlipaySyncService.STATUS_SUCCESS)
                        .or().isNull("contract_alipay_file_id")
                        .or().eq("contract_alipay_file_id", ""))
                .orderByAsc("order_id"));
    }

    /**
     * 单笔补偿复用原回传服务，确保生成 PDF、上传支付宝、订单状态和操作台账口径完全一致。
     */
    private void syncOne(Order order, BatchResult result) {
        if (order == null || order.getOrderId() == null) {
            return;
        }
        result.processedCount++;
        try {
            contractAlipaySyncService.syncContractAfterReceive(order.getOrderId(), "定时任务回传协议");
            Order latest = orderMapper.selectById(order.getOrderId());
            if (isSyncSuccess(latest)) {
                result.successCount++;
                result.successOrderNos.add(orderLabel(latest));
            } else {
                result.failedCount++;
                result.failedOrderNos.add(orderLabel(latest == null ? order : latest));
            }
        } catch (Exception e) {
            result.failedCount++;
            result.failedOrderNos.add(orderLabel(order));
            log.warn("批量补偿租赁合同回传单笔异常: orderId={}, msg={}", order.getOrderId(), e.getMessage(), e);
        }
    }

    private boolean isSyncSuccess(Order order) {
        return order != null
                && RentContractAlipaySyncService.STATUS_SUCCESS.equals(order.getContractAlipaySyncStatus())
                && StringUtils.hasText(order.getContractAlipayFileId());
    }

    /**
     * 任务执行完成后发送飞书汇总。飞书失败不能影响定时任务结果，所以只记录本地日志。
     */
    private void sendSummary(BatchResult result) {
        try {
            Map<String, String> metrics = new LinkedHashMap<String, String>();
            metrics.put("扫描范围", "今年用户确认收货后的未成功回传订单");
            metrics.put("扫描订单数", String.valueOf(result.scannedCount));
            metrics.put("实际处理数", String.valueOf(result.processedCount));
            metrics.put("回传成功数", String.valueOf(result.successCount));
            metrics.put("回传失败数", String.valueOf(result.failedCount));
            metrics.put("执行耗时", result.durationMillis + "ms");
            metrics.put("成功订单", previewOrderNos(result.successOrderNos));
            metrics.put("失败订单", previewOrderNos(result.failedOrderNos));
            feishuNotifyService.sendPeriodicReportCard(
                    "alipay",
                    "支付宝租赁",
                    "租赁合同回传补偿",
                    formatDate(result.startedAt),
                    formatDateTime(result.startedAt) + " - " + formatDateTime(result.finishedAt),
                    "定时任务",
                    result.finishedAt,
                    metrics,
                    feishuNotifyProperties.getAlipayLinks().getOrderList()
            );
        } catch (Exception e) {
            log.warn("发送租赁合同回传补偿飞书汇总失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 今年 1 月 1 日 00:00:00 的毫秒时间戳，跟 rent_order.created_at 的毫秒口径保持一致。
     */
    private long currentYearStartMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private String orderLabel(Order order) {
        if (order == null) {
            return "-";
        }
        if (StringUtils.hasText(order.getOrderNo())) {
            return order.getOrderNo();
        }
        return String.valueOf(order.getOrderId());
    }

    private String previewOrderNos(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return "-";
        }
        List<String> preview = orderNos.size() > MAX_ORDER_NO_PREVIEW
                ? orderNos.subList(0, MAX_ORDER_NO_PREVIEW)
                : orderNos;
        String suffix = orderNos.size() > MAX_ORDER_NO_PREVIEW
                ? " 等" + orderNos.size() + "笔"
                : "";
        return String.join("、", preview) + suffix;
    }

    private String formatDate(Date date) {
        return date == null ? "-" : new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    private String formatDateTime(Date date) {
        return date == null ? "-" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    /**
     * 批量补偿执行结果。
     */
    @Getter
    public static class BatchResult {
        private Date startedAt;
        private Date finishedAt;
        private long durationMillis;
        private int scannedCount;
        private int processedCount;
        private int successCount;
        private int failedCount;
        private final List<String> successOrderNos = new ArrayList<String>();
        private final List<String> failedOrderNos = new ArrayList<String>();
    }
}

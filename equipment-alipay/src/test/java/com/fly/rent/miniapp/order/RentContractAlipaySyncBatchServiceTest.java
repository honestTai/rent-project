package com.fly.rent.miniapp.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.common.notify.feishu.FeishuNotifyProperties;
import com.common.notify.feishu.FeishuNotifyService;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RentContractAlipaySyncBatchServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RentContractAlipaySyncService contractAlipaySyncService;
    @Mock
    private FeishuNotifyService feishuNotifyService;
    @Mock
    private FeishuNotifyProperties feishuNotifyProperties;

    @Test
    void syncUnsignedReceivedOrdersCountsSuccessAndFailureAndSendsSummary() {
        Order first = order(1, "ORD-1", null, null);
        Order second = order(2, "ORD-2", null, null);
        when(orderMapper.selectList(any())).thenReturn(Arrays.asList(first, second));
        when(orderMapper.selectById(1)).thenReturn(order(1, "ORD-1", RentContractAlipaySyncService.STATUS_SUCCESS, "FILE-1"));
        when(orderMapper.selectById(2)).thenReturn(order(2, "ORD-2", RentContractAlipaySyncService.STATUS_FAILED, null));
        when(feishuNotifyProperties.getAlipayLinks()).thenReturn(alipayLinks());

        RentContractAlipaySyncBatchService.BatchResult result = service().syncUnsignedReceivedOrders();

        assertEquals(2, result.getScannedCount());
        assertEquals(2, result.getProcessedCount());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailedCount());
        verify(contractAlipaySyncService).syncContractAfterReceive(1, "定时任务回传协议");
        verify(contractAlipaySyncService).syncContractAfterReceive(2, "定时任务回传协议");
        verifySummaryMetric("回传成功数", "1");
        verifySummaryMetric("回传失败数", "1");
    }

    @Test
    void syncUnsignedReceivedOrdersSendsSummaryWhenNoPendingOrders() {
        when(orderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(feishuNotifyProperties.getAlipayLinks()).thenReturn(alipayLinks());

        RentContractAlipaySyncBatchService.BatchResult result = service().syncUnsignedReceivedOrders();

        assertEquals(0, result.getScannedCount());
        assertEquals(0, result.getProcessedCount());
        verifySummaryMetric("扫描订单数", "0");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void syncUnsignedReceivedOrdersLimitsQueryToCurrentYearAndAfterReceiveStatuses() {
        when(orderMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(feishuNotifyProperties.getAlipayLinks()).thenReturn(alipayLinks());

        service().syncUnsignedReceivedOrders();

        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).selectList(queryCaptor.capture());
        String sqlSegment = queryCaptor.getValue().getSqlSegment();
        assertTrue(sqlSegment.contains("alipay_status IN"));
        assertTrue(sqlSegment.contains("created_at >="));
        assertTrue(sqlSegment.contains("contract_alipay_sync_status"));
        verifySummaryMetric("扫描范围", "今年用户确认收货后的未成功回传订单");
    }

    @SuppressWarnings("unchecked")
    private void verifySummaryMetric(String key, String value) {
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(feishuNotifyService).sendPeriodicReportCard(
                eq("alipay"),
                eq("支付宝租赁"),
                eq("租赁合同回传补偿"),
                any(),
                any(),
                eq("定时任务"),
                any(),
                captor.capture(),
                eq("https://example.test/rent/orders")
        );
        Map<String, String> metrics = captor.getValue();
        assertTrue(metrics.containsKey(key));
        assertEquals(value, metrics.get(key));
    }

    private RentContractAlipaySyncBatchService service() {
        return new RentContractAlipaySyncBatchService(
                orderMapper,
                contractAlipaySyncService,
                feishuNotifyService,
                feishuNotifyProperties
        );
    }

    private Order order(Integer id, String orderNo, String syncStatus, String fileId) {
        Order order = new Order();
        order.setOrderId(id);
        order.setOrderNo(orderNo);
        order.setAlipayStatus("RECEIVED");
        order.setContractAlipaySyncStatus(syncStatus);
        order.setContractAlipayFileId(fileId);
        return order;
    }

    private FeishuNotifyProperties.AlipayLinks alipayLinks() {
        FeishuNotifyProperties.AlipayLinks links = new FeishuNotifyProperties.AlipayLinks();
        links.setOrderList("https://example.test/rent/orders");
        return links;
    }
}

package com.fly.rent.web.analytics;

import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.RentInstallmentInfoEntityMapper;
import com.fly.rent.mapper.UserMapper;
import com.fly.rent.miniapp.cache.MiniappCacheService;
import com.fly.rent.web.support.WebRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class WebAnalyticsTopicsServiceTest {

    @Test
    void subjectsExcludeClosedOrdersFromRevenueAndFinishedFulfillment() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        GoodMapper goodMapper = mock(GoodMapper.class);
        MiniappCacheService cacheService = mock(MiniappCacheService.class);
        AlipayAnalyticsAmountService amountService = spy(new AlipayAnalyticsAmountService(
                mock(RentInstallmentInfoEntityMapper.class)));
        WebAnalyticsTopicsService service = new WebAnalyticsTopicsService(
                orderMapper, userMapper, goodMapper, cacheService, amountService);

        List<Order> currentOrders = Arrays.asList(
                order(1, 1, 50_000),
                order(2, 1, 58_200)
        );
        Map<Integer, Long> paidAmountCents = mapOfAmounts(1, 48_000L);
        when(orderMapper.selectList(any()))
                .thenReturn(currentOrders)
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());
        when(userMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(goodMapper.selectCount(any())).thenReturn(0L);
        doReturn(paidAmountCents).when(amountService).paidInstallmentAmountCents(anyList());

        Map<String, Object> subjects = service.subjects(WebRequest.of(mapOf(
                "periodType", "MONTH",
                "targetDate", "2099-06-15",
                "timezone", "Asia/Shanghai"
        )));

        Map<?, ?> revenueSummary = summary(subjects, "revenueAnalysis");
        assertEquals(0.0, revenueSummary.get("totalRevenue"));
        assertEquals(0.0, revenueSummary.get("totalPaid"));
        assertEquals(0.0, revenueSummary.get("avgOrderValue"));

        Map<?, ?> deviceSummary = summary(subjects, "deviceAnalysis");
        assertEquals(0.0, deviceSummary.get("totalRevenue"));
        List<?> deviceRows = rows(subjects, "deviceAnalysis", "deviceRows");
        assertEquals(0.0, ((Map<?, ?>) deviceRows.get(0)).get("totalAmount"));

        Map<?, ?> rentalSummary = summary(subjects, "rentalAnalysis");
        assertEquals(0.0, rentalSummary.get("finishedRate"));
        List<?> performanceRows = rows(subjects, "rentalAnalysis", "performanceRows");
        assertEquals(0L, valueByLabel(performanceRows, "已完结"));
        assertEquals(2L, valueByLabel(performanceRows, "已取消"));
    }

    private Order order(Integer orderId, Integer userId, Integer orderTotal) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setGoodTitle("苹果电脑-免押租赁-macbook air");
        order.setAlipayStatus(AlipayRentConstants.STATUS_CLOSED);
        order.setOrderTotal(orderTotal);
        order.setOrderDeposit(0);
        order.setOrderKeep(120);
        order.setCreatetime(4_085_875_200_000L);
        order.setAddr("四川省成都市高新区");
        return order;
    }

    private Map<?, ?> summary(Map<String, Object> subjects, String section) {
        return (Map<?, ?>) ((Map<?, ?>) subjects.get(section)).get("summary");
    }

    private List<?> rows(Map<String, Object> subjects, String section, String rowKey) {
        return (List<?>) ((Map<?, ?>) subjects.get(section)).get(rowKey);
    }

    private long valueByLabel(List<?> rows, String label) {
        return rows.stream()
                .map(Map.class::cast)
                .filter(row -> label.equals(row.get("label")))
                .map(row -> ((Number) row.get("value")).longValue())
                .findFirst()
                .orElseThrow(AssertionError::new);
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private Map<Integer, Long> mapOfAmounts(Object... pairs) {
        Map<Integer, Long> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Integer) pairs[i], (Long) pairs[i + 1]);
        }
        return map;
    }
}

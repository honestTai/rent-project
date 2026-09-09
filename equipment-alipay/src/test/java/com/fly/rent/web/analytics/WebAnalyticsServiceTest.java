package com.fly.rent.web.analytics;

import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.RentInstallmentInfoEntityMapper;
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

class WebAnalyticsServiceTest {

    @Test
    void dashboardUsesPaidInstallmentsForRevenueWhenOrdersAreClosed() {
        OrderMapper orderMapper = mock(OrderMapper.class);
        MiniappCacheService cacheService = mock(MiniappCacheService.class);
        AlipayAnalyticsAmountService amountService = spy(new AlipayAnalyticsAmountService(
                mock(RentInstallmentInfoEntityMapper.class)));
        WebAnalyticsService service = new WebAnalyticsService(orderMapper, cacheService, amountService);

        List<Order> currentOrders = Arrays.asList(
                order(1, 1, 50_000),
                order(2, 1, 58_200)
        );
        Map<Integer, Long> paidAmountCents = mapOfAmounts(1, 48_000L);
        when(orderMapper.selectList(any()))
                .thenReturn(currentOrders)
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());
        doReturn(paidAmountCents).when(amountService).paidInstallmentAmountCents(anyList());

        Map<String, Object> dashboard = service.dashboard(WebRequest.of(mapOf(
                "periodType", "MONTH",
                "targetDate", "2099-06-15",
                "timezone", "Asia/Shanghai"
        )));

        Map<?, ?> overview = (Map<?, ?>) dashboard.get("overview");
        assertEquals(2, overview.get("totalOrders"));
        assertEquals(0L, overview.get("totalRevenueCents"));
        assertEquals(0.0, overview.get("totalRevenueYuan"));
        assertEquals(0L, overview.get("totalPaidCents"));
        assertEquals(0.0, overview.get("totalPaidYuan"));
        assertEquals(0L, overview.get("finishedOrders"));
        assertEquals("0.0", overview.get("finishedRate"));

        Map<?, ?> trend = (Map<?, ?>) dashboard.get("trend");
        List<?> points = (List<?>) trend.get("points");
        Map<?, ?> firstOrderDay = (Map<?, ?>) points.stream()
                .map(Map.class::cast)
                .filter(point -> "2099-06-23".equals(point.get("date")))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertEquals(2L, firstOrderDay.get("orderCount"));
        assertEquals(0L, firstOrderDay.get("revenueCents"));
        assertEquals(0.0, firstOrderDay.get("orderAmount"));
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

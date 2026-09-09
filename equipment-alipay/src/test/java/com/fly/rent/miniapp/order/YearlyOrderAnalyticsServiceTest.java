package com.fly.rent.miniapp.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.common.support.RentApiException;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.order.dto.YearlyOrderAnalyticsAggregate;
import com.fly.rent.miniapp.order.dto.YearlyOrderAnalyticsResponse;
import com.fly.rent.web.analytics.AlipayAnalyticsAmountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YearlyOrderAnalyticsServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    @Mock OrderMapper orderMapper;
    @Mock RentCurrentUserService currentUserService;
    @Mock AlipayAnalyticsAmountService amountService;
    MiniappOrderAnalyticsService service;

    @BeforeEach void setUp() {
        service = new MiniappOrderAnalyticsService(orderMapper, currentUserService, amountService, ZONE,
                Clock.fixed(Instant.parse("2026-07-15T04:00:00Z"), ZONE));
        when(currentUserService.requireUserUuid()).thenReturn("user-a");
        lenient().when(orderMapper.selectYearlyAnalytics(anyString(), anyLong(), anyLong(), anyString(), anyList(), anyString(), anyList()))
                .thenReturn(Collections.emptyList());
        lenient().when(orderMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(amountService.paidInstallmentAmountCents(anyList())).thenReturn(Collections.emptyMap());
    }

    @Test void usesJwtUserShanghaiCrossYearRangeAndFormalStatuses() {
        service.getYearlyAnalytics("2026");
        verify(orderMapper).selectYearlyAnalytics(eq("user-a"), eq(epoch("2026-01-01")), eq(epoch("2027-01-01")),
                eq("Asia/Shanghai"), eq(Arrays.asList("CREATED", "SIGNED", "APPROVED", "PAID", "DELIVERED",
                        "RECEIVED", "RETURN_DELIVERED", "RETURN_RECEIVED")), eq("FINISHED"),
                eq(Arrays.asList("PENDING_CANCEL", "CLOSED", "CANCELLED", "REFUNDING", "REFUNDED")));
        verify(currentUserService).requireUserUuid();
        ArgumentCaptor<QueryWrapper<Order>> wrapperCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(orderMapper).selectList(wrapperCaptor.capture());
        assertEquals("order_id,alipay_status,created_at AS createtime", wrapperCaptor.getValue().getSqlSelect());
    }

    @Test void fillsTwelveMonthsAndKeepsTotalsConsistent() {
        YearlyOrderAnalyticsAggregate january = row("2026-01", 2, 1, 1, 0, 1);
        YearlyOrderAnalyticsAggregate december = row("2026-12", 1, 0, 0, 1, 0);
        when(orderMapper.selectYearlyAnalytics(anyString(), anyLong(), anyLong(), anyString(), anyList(), anyString(), anyList()))
                .thenReturn(Arrays.asList(january, december));
        Order janOrder = order(1, "2026-01-03", "FINISHED");
        Order decOrder = order(2, "2026-12-31", "REFUNDED");
        when(orderMapper.selectList(any())).thenReturn(Arrays.asList(janOrder, decOrder));
        when(amountService.paidInstallmentAmountCents(anyList())).thenReturn(Map.of(1, 18000L, 2, 9000L));
        when(amountService.sumRevenueAmountCents(argThat(list -> list.size() == 1 && list.get(0).getOrderId() == 1), anyMap())).thenReturn(18000L);

        YearlyOrderAnalyticsResponse response = service.getYearlyAnalytics("2026");
        assertEquals(12, response.getMonths().size());
        assertEquals("2026-01", response.getMonths().get(0).getMonth());
        assertEquals("2026-12", response.getMonths().get(11).getMonth());
        assertEquals(3, response.getOrderCount());
        assertEquals(18000, response.getRentAmountCents());
        assertEquals(1, response.getRenewOrderCount());
        assertEquals(response.getActiveCount(), response.getStatusDistribution().get(0).getCount());
        assertEquals(0, response.getMonths().get(1).getOrderCount());
        verify(amountService, times(1)).paidInstallmentAmountCents(anyList());
        verify(orderMapper, times(1)).selectList(any());
    }

    @Test void emptyYearReturnsCompleteZeroShapeAndDefaultsCurrentYear() {
        YearlyOrderAnalyticsResponse response = service.getYearlyAnalytics(null);
        assertEquals(2026, response.getYear());
        assertEquals(12, response.getMonths().size());
        assertEquals(3, response.getStatusDistribution().size());
        assertTrue(response.getMonths().stream().allMatch(m -> m.getOrderCount() == 0 && m.getRentAmountCents() == 0));
    }

    @Test void rejectsMalformedAndFutureYearsWithoutQuerying() {
        RentApiException malformed = assertThrows(RentApiException.class, () -> service.getYearlyAnalytics("26"));
        assertEquals(400, malformed.getCode());
        RentApiException future = assertThrows(RentApiException.class, () -> service.getYearlyAnalytics("2027"));
        assertEquals(400, future.getCode());
        assertEquals("禁止查询未来年份", future.getMessage());
        verifyNoInteractions(orderMapper);
    }

    @Test void returnsExplicitBusinessErrorWhenCreatedTimeCannotBeMapped() {
        Order order = new Order();
        order.setOrderId(1);
        order.setAlipayStatus("FINISHED");
        when(orderMapper.selectList(any())).thenReturn(Collections.singletonList(order));

        RentApiException exception = assertThrows(
                RentApiException.class, () -> service.getYearlyAnalytics("2026"));

        assertEquals(500, exception.getCode());
        assertEquals("年度租赁订单创建时间缺失", exception.getMessage());
    }

    private YearlyOrderAnalyticsAggregate row(String month, long count, long active, long finished, long closed, long renew) {
        YearlyOrderAnalyticsAggregate row = new YearlyOrderAnalyticsAggregate();
        row.setMonth(month); row.setOrderCount(count); row.setActiveCount(active); row.setFinishedCount(finished);
        row.setClosedCount(closed); row.setRenewOrderCount(renew); return row;
    }
    private Order order(int id, String date, String status) {
        Order order = new Order(); order.setOrderId(id); order.setCreatetime(epoch(date)); order.setAlipayStatus(status); return order;
    }
    private long epoch(String date) { return LocalDate.parse(date).atStartOfDay(ZONE).toInstant().toEpochMilli(); }
}

package com.fly.rent.miniapp.order;

import com.fly.rent.common.support.RentApiException;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.order.dto.MonthlyOrderAnalyticsAggregate;
import com.fly.rent.miniapp.order.dto.MonthlyOrderAnalyticsResponse;
import com.fly.rent.web.analytics.AlipayAnalyticsAmountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MiniappOrderAnalyticsServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RentCurrentUserService currentUserService;
    @Mock
    private AlipayAnalyticsAmountService amountService;

    private MiniappOrderAnalyticsService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T04:00:00Z"), BUSINESS_ZONE);
        service = new MiniappOrderAnalyticsService(orderMapper, currentUserService, amountService, BUSINESS_ZONE, clock);
        when(currentUserService.requireUserUuid()).thenReturn("user-a");
        lenient().when(orderMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void usesJwtSessionUserAndLeftClosedRightOpenCrossYearRange() {
        MonthlyOrderAnalyticsAggregate aggregate = new MonthlyOrderAnalyticsAggregate();
        when(orderMapper.selectMonthlyAnalytics(
                eq("user-a"),
                eq(epoch("2026-12-01")),
                eq(epoch("2027-01-01")),
                eq(epoch("2026-12-08")),
                eq(epoch("2026-12-15")),
                eq(epoch("2026-12-22")),
                anyList(),
                eq("FINISHED"),
                eq(Arrays.asList("PENDING_CANCEL", "CLOSED", "CANCELLED", "REFUNDING", "REFUNDED"))
        )).thenReturn(aggregate);

        MonthlyOrderAnalyticsResponse response = service.getMonthlyAnalytics("2026-12");

        assertEquals("2026-12", response.getMonth());
        assertEquals("Asia/Shanghai", response.getTimezone());
        assertEquals("2026-12-22", response.getBuckets().get(3).getStartDate());
        assertEquals("2026-12-31", response.getBuckets().get(3).getEndDate());
        verify(currentUserService).requireUserUuid();
    }

    @Test
    void mapsDatabaseAggregatesWithoutChangingCentsOrRenewalCounts() {
        MonthlyOrderAnalyticsAggregate aggregate = new MonthlyOrderAnalyticsAggregate();
        aggregate.setOrderCount(8);
        aggregate.setActiveCount(2);
        aggregate.setFinishedCount(5);
        aggregate.setClosedCount(1);
        aggregate.setRenewOrderCount(1);
        aggregate.setRentAmountCents(168800);
        aggregate.setBucket1Count(2);
        aggregate.setBucket2Count(3);
        aggregate.setBucket3Count(1);
        aggregate.setBucket4Count(2);
        stubJuly(aggregate);
        List<Order> orders = Arrays.asList(
                order(1, "2026-07-03"), order(2, "2026-07-10"),
                order(3, "2026-07-18"), order(4, "2026-07-28"));
        when(orderMapper.selectList(any())).thenReturn(orders);
        when(amountService.paidInstallmentAmountCents(orders)).thenReturn(Map.of(
                1, 18000L, 2, 32000L, 3, 48000L, 4, 70800L));
        when(amountService.sumRevenueAmountCents(anyList(), any())).thenAnswer(invocation -> {
            List<Order> bucketOrders = invocation.getArgument(0);
            Map<Integer, Long> paid = invocation.getArgument(1);
            return bucketOrders.stream().mapToLong(order -> paid.getOrDefault(order.getOrderId(), 0L)).sum();
        });

        MonthlyOrderAnalyticsResponse response = service.getMonthlyAnalytics("2026-07");

        assertEquals(8, response.getSummary().getOrderCount());
        assertEquals(2, response.getSummary().getActiveCount());
        assertEquals(5, response.getSummary().getFinishedCount());
        assertEquals(1, response.getSummary().getClosedCount());
        assertEquals(1, response.getSummary().getRenewOrderCount());
        assertEquals(168800, response.getSummary().getRentAmountCents());
        assertEquals(Arrays.asList(2L, 3L, 1L, 2L), Arrays.asList(
                response.getBuckets().get(0).getOrderCount(),
                response.getBuckets().get(1).getOrderCount(),
                response.getBuckets().get(2).getOrderCount(),
                response.getBuckets().get(3).getOrderCount()
        ));
        assertEquals(Arrays.asList(18000L, 32000L, 48000L, 70800L), Arrays.asList(
                response.getBuckets().get(0).getRentAmountCents(),
                response.getBuckets().get(1).getRentAmountCents(),
                response.getBuckets().get(2).getRentAmountCents(),
                response.getBuckets().get(3).getRentAmountCents()
        ));
        assertEquals(response.getSummary().getRentAmountCents(), response.getBuckets().stream()
                .mapToLong(MonthlyOrderAnalyticsResponse.Bucket::getRentAmountCents).sum());
    }

    @Test
    void noOrderMonthReturnsFourCompleteZeroBuckets() {
        stubJuly(new MonthlyOrderAnalyticsAggregate());

        MonthlyOrderAnalyticsResponse response = service.getMonthlyAnalytics(null);

        assertEquals("2026-07", response.getMonth());
        assertEquals(0, response.getSummary().getOrderCount());
        assertEquals(4, response.getBuckets().size());
        assertEquals("2026-07-01", response.getBuckets().get(0).getStartDate());
        assertEquals("2026-07-07", response.getBuckets().get(0).getEndDate());
        assertEquals("2026-07-22", response.getBuckets().get(3).getStartDate());
        assertEquals("2026-07-31", response.getBuckets().get(3).getEndDate());
        response.getBuckets().forEach(bucket -> {
            assertEquals(0, bucket.getOrderCount());
            assertEquals(0, bucket.getRentAmountCents());
        });
    }

    @Test
    void rejectsMalformedMonthInsteadOfFallingBackToCurrentMonth() {
        RentApiException exception = assertThrows(
                RentApiException.class,
                () -> service.getMonthlyAnalytics("2026-7")
        );

        assertEquals(400, exception.getCode());
        assertEquals("month 格式必须为 YYYY-MM", exception.getMessage());
        verifyNoInteractions(orderMapper);
    }

    @Test
    void passesOnlyExactFormalActiveStatusCodesToSql() {
        MonthlyOrderAnalyticsAggregate aggregate = new MonthlyOrderAnalyticsAggregate();
        when(orderMapper.selectMonthlyAnalytics(
                eq("user-a"),
                eq(epoch("2026-07-01")),
                eq(epoch("2026-08-01")),
                eq(epoch("2026-07-08")),
                eq(epoch("2026-07-15")),
                eq(epoch("2026-07-22")),
                eq(Arrays.asList(
                        "CREATED", "SIGNED", "APPROVED", "PAID", "DELIVERED",
                        "RECEIVED", "RETURN_DELIVERED", "RETURN_RECEIVED"
                )),
                eq("FINISHED"),
                eq(Arrays.asList("PENDING_CANCEL", "CLOSED", "CANCELLED", "REFUNDING", "REFUNDED"))
        )).thenReturn(aggregate);

        service.getMonthlyAnalytics("2026-07");

        verify(orderMapper).selectMonthlyAnalytics(
                eq("user-a"),
                eq(epoch("2026-07-01")),
                eq(epoch("2026-08-01")),
                eq(epoch("2026-07-08")),
                eq(epoch("2026-07-15")),
                eq(epoch("2026-07-22")),
                eq(Arrays.asList(
                        "CREATED", "SIGNED", "APPROVED", "PAID", "DELIVERED",
                        "RECEIVED", "RETURN_DELIVERED", "RETURN_RECEIVED"
                )),
                eq("FINISHED"),
                eq(Arrays.asList("PENDING_CANCEL", "CLOSED", "CANCELLED", "REFUNDING", "REFUNDED"))
        );
    }

    private void stubJuly(MonthlyOrderAnalyticsAggregate aggregate) {
        when(orderMapper.selectMonthlyAnalytics(
                eq("user-a"),
                eq(epoch("2026-07-01")),
                eq(epoch("2026-08-01")),
                eq(epoch("2026-07-08")),
                eq(epoch("2026-07-15")),
                eq(epoch("2026-07-22")),
                anyList(),
                eq("FINISHED"),
                eq(Arrays.asList("PENDING_CANCEL", "CLOSED", "CANCELLED", "REFUNDING", "REFUNDED"))
        )).thenReturn(aggregate);
    }

    private long epoch(String date) {
        return LocalDate.parse(date).atStartOfDay(BUSINESS_ZONE).toInstant().toEpochMilli();
    }

    private Order order(int id, String date) {
        Order order = new Order();
        order.setOrderId(id);
        order.setAlipayStatus("PAID");
        order.setCreatetime(epoch(date));
        return order;
    }
}

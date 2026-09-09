package com.fly.rent.report.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.report.dto.PeriodicReportGenerateDto;
import com.fly.rent.report.entity.PeriodicReport;
import com.fly.rent.report.mapper.PeriodicReportMapper;
import com.fly.rent.web.analytics.WebAnalyticsService;
import com.fly.rent.web.analytics.WebAnalyticsTopicsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PeriodicReportServiceImplTest {

    @Test
    void manualGenerateRefreshesExistingReportSnapshot() {
        PeriodicReportMapper reportMapper = mock(PeriodicReportMapper.class);
        WebAnalyticsService analyticsService = mock(WebAnalyticsService.class);
        WebAnalyticsTopicsService topicsService = mock(WebAnalyticsTopicsService.class);
        PeriodicReportServiceImpl service = new PeriodicReportServiceImpl();
        ReflectionTestUtils.setField(service, "periodicReportMapper", reportMapper);
        ReflectionTestUtils.setField(service, "webAnalyticsService", analyticsService);
        ReflectionTestUtils.setField(service, "webAnalyticsTopicsService", topicsService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        PeriodicReport existing = new PeriodicReport();
        existing.setId(4);
        existing.setReportType("WEEK");
        existing.setPeriodKey("2026-W26");
        existing.setTotalRevenue(new BigDecimal("362.00"));
        existing.setProfitAmount(new BigDecimal("120.00"));
        existing.setSalesCount(5);
        existing.setExtraMetric1("0");
        existing.setExtraMetric2("5");
        existing.setGenerateMode("AUTO");
        when(reportMapper.selectOne(any())).thenReturn(existing);
        when(analyticsService.dashboard(any())).thenReturn(dashboardPayload());
        when(topicsService.subjects(any())).thenReturn(subjectsPayload());

        PeriodicReportGenerateDto dto = new PeriodicReportGenerateDto();
        dto.setReportType("WEEK");
        dto.setTargetDate(date(2026, 6, 24));

        PeriodicReport report = service.generateReport(dto);

        assertSame(existing, report);
        assertEquals(new BigDecimal("0.00"), report.getTotalRevenue());
        assertEquals(new BigDecimal("0.00"), report.getProfitAmount());
        assertEquals(5, report.getSalesCount());
        assertEquals("0", report.getExtraMetric1());
        assertEquals("5", report.getExtraMetric2());
        assertEquals("AUTO", report.getGenerateMode());
        assertTrue(report.getContentJson().contains("summaryCards"));
        verify(reportMapper).updateById(existing);
        verify(reportMapper, never()).insert(any());
    }

    private Map<String, Object> dashboardPayload() {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("overview", mapOf(
                "totalRevenueCents", 0L,
                "totalPaidCents", 0L,
                "totalOrders", 5,
                "activeRentals", 0,
                "overdueCount", 0
        ));
        dashboard.put("comparison", mapOf(
                "previousRevenueCents", 0L,
                "revenueChange", 0D,
                "previousOrders", 0,
                "ordersChange", 0D
        ));
        dashboard.put("financialReport", mapOf());
        return dashboard;
    }

    private Map<String, Object> subjectsPayload() {
        Map<String, Object> subjects = new HashMap<>();
        subjects.put("orderAnalysis", mapOf("summary", mapOf(
                "cancelledOrders", 5,
                "finishedRate", 0D
        )));
        subjects.put("regionAnalysis", mapOf("summary", mapOf(
                "coveredProvinceCount", 0
        )));
        subjects.put("revenueAnalysis", mapOf("summary", mapOf()));
        subjects.put("rentalAnalysis", mapOf("summary", mapOf()));
        subjects.put("deviceAnalysis", mapOf("summary", mapOf()));
        return subjects;
    }

    private Map<String, Object> mapOf(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day)
                .atStartOfDay(ZoneId.of("Asia/Shanghai"))
                .toInstant());
    }
}

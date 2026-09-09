package com.fly.rent.miniapp.order.dto;

import lombok.Value;

import java.util.List;

/** 小程序当前用户年度租赁统计响应。金额单位均为分。 */
@Value
public class YearlyOrderAnalyticsResponse {
    int year;
    String timezone;
    long orderCount;
    long activeCount;
    long finishedCount;
    long closedCount;
    long renewOrderCount;
    long rentAmountCents;
    List<MonthItem> months;
    List<StatusItem> statusDistribution;

    @Value
    public static class MonthItem {
        String month;
        long orderCount;
        long rentAmountCents;
    }

    @Value
    public static class StatusItem {
        String status;
        long count;
    }
}

package com.fly.rent.miniapp.order.dto;

import lombok.Value;

import java.util.List;

/**
 * 小程序个人中心月度租赁分析响应数据。
 */
@Value
public class MonthlyOrderAnalyticsResponse {

    String month;
    String timezone;
    Summary summary;
    List<Bucket> buckets;

    @Value
    public static class Summary {
        long orderCount;
        long activeCount;
        long finishedCount;
        long closedCount;
        long renewOrderCount;
        long rentAmountCents;
    }

    @Value
    public static class Bucket {
        String startDate;
        String endDate;
        long orderCount;
        long rentAmountCents;
    }
}

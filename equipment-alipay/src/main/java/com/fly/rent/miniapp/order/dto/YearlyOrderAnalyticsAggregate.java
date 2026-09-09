package com.fly.rent.miniapp.order.dto;

import lombok.Data;

/** 年度订单按自然月聚合结果。 */
@Data
public class YearlyOrderAnalyticsAggregate {
    private String month;
    private long orderCount;
    private long activeCount;
    private long finishedCount;
    private long closedCount;
    private long renewOrderCount;
}

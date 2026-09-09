package com.fly.rent.miniapp.order.dto;

import lombok.Data;

/**
 * 月度订单数据库聚合结果。所有计数和金额均由 SQL 直接计算。
 */
@Data
public class MonthlyOrderAnalyticsAggregate {

    private long orderCount;
    private long activeCount;
    private long finishedCount;
    private long closedCount;
    private long renewOrderCount;
    private long rentAmountCents;
    private long bucket1Count;
    private long bucket2Count;
    private long bucket3Count;
    private long bucket4Count;
}

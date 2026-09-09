package com.fly.rent.common.order;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 支付宝租赁订单正式状态枚举。
 *
 * <p>月度分析只按这里的精确状态码分类，不做前缀、包含或大小写模糊匹配。
 * RETURN_RECEIVED 仍处于归还履约收尾阶段，按个人中心月度分析口径归入 ACTIVE；
 * 只有 FINISHED 归入完成；取消、退款及关闭态统一归入关闭，避免被误算为进行中。</p>
 */
public enum RentOrderStatus {

    CREATED(MonthlyAnalyticsGroup.ACTIVE),
    SIGNED(MonthlyAnalyticsGroup.ACTIVE),
    APPROVED(MonthlyAnalyticsGroup.ACTIVE),
    PAID(MonthlyAnalyticsGroup.ACTIVE),
    DELIVERED(MonthlyAnalyticsGroup.ACTIVE),
    RECEIVED(MonthlyAnalyticsGroup.ACTIVE),
    RETURN_DELIVERED(MonthlyAnalyticsGroup.ACTIVE),
    RETURN_RECEIVED(MonthlyAnalyticsGroup.ACTIVE),
    PENDING_CANCEL(MonthlyAnalyticsGroup.CLOSED),
    FINISHED(MonthlyAnalyticsGroup.FINISHED),
    CLOSED(MonthlyAnalyticsGroup.CLOSED),
    CANCELLED(MonthlyAnalyticsGroup.CLOSED),
    REFUNDING(MonthlyAnalyticsGroup.CLOSED),
    REFUNDED(MonthlyAnalyticsGroup.CLOSED);

    private final MonthlyAnalyticsGroup monthlyAnalyticsGroup;

    RentOrderStatus(MonthlyAnalyticsGroup monthlyAnalyticsGroup) {
        this.monthlyAnalyticsGroup = monthlyAnalyticsGroup;
    }

    public String getCode() {
        return name();
    }

    public static List<String> codesForMonthlyGroup(MonthlyAnalyticsGroup group) {
        return Collections.unmodifiableList(Arrays.stream(values())
                .filter(status -> status.monthlyAnalyticsGroup == group)
                .map(RentOrderStatus::getCode)
                .collect(Collectors.toList()));
    }

    /**
     * 商品销量只统计已经付款并进入履约的订单，不把建单、签约或审核中的意向单计为销量。
     */
    public static List<String> codesForGoodsSales() {
        return Collections.unmodifiableList(Arrays.asList(
                PAID.getCode(), DELIVERED.getCode(), RECEIVED.getCode(),
                RETURN_DELIVERED.getCode(), RETURN_RECEIVED.getCode(), FINISHED.getCode()
        ));
    }

    public enum MonthlyAnalyticsGroup {
        ACTIVE,
        FINISHED,
        CLOSED
    }
}

package com.fly.rent.miniapp.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.common.order.RentOrderStatus;
import com.fly.rent.common.order.RentOrderStatus.MonthlyAnalyticsGroup;
import com.fly.rent.common.support.RentApiException;
import com.fly.rent.common.user.RentCurrentUserService;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.order.dto.MonthlyOrderAnalyticsAggregate;
import com.fly.rent.miniapp.order.dto.MonthlyOrderAnalyticsResponse;
import com.fly.rent.miniapp.order.dto.YearlyOrderAnalyticsAggregate;
import com.fly.rent.miniapp.order.dto.YearlyOrderAnalyticsResponse;
import com.fly.rent.web.analytics.AlipayAnalyticsAmountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 小程序个人中心月度租赁分析服务。
 */
@Service
public class MiniappOrderAnalyticsService {

    private static final Pattern MONTH_PATTERN = Pattern.compile("^[0-9]{4}-(0[1-9]|1[0-2])$");
    private static final Pattern YEAR_PATTERN = Pattern.compile("^[0-9]{4}$");

    private final OrderMapper orderMapper;
    private final RentCurrentUserService currentUserService;
    private final AlipayAnalyticsAmountService amountService;
    private final ZoneId businessZone;
    private final Clock clock;

    @Autowired
    public MiniappOrderAnalyticsService(
            OrderMapper orderMapper,
            RentCurrentUserService currentUserService,
            AlipayAnalyticsAmountService amountService,
            @Value("${rent.business-timezone:Asia/Shanghai}") String businessTimezone
    ) {
        this(orderMapper, currentUserService, amountService, ZoneId.of(businessTimezone), null);
    }

    MiniappOrderAnalyticsService(
            OrderMapper orderMapper,
            RentCurrentUserService currentUserService,
            AlipayAnalyticsAmountService amountService,
            ZoneId businessZone,
            Clock clock
    ) {
        this.orderMapper = orderMapper;
        this.currentUserService = currentUserService;
        this.amountService = amountService;
        this.businessZone = businessZone;
        this.clock = clock == null ? Clock.system(businessZone) : clock;
    }

    public MonthlyOrderAnalyticsResponse getMonthlyAnalytics(String requestedMonth) {
        String userUuid = currentUserService.requireUserUuid();
        YearMonth month = resolveMonth(requestedMonth);

        LocalDate firstDay = month.atDay(1);
        LocalDate nextMonthFirstDay = month.plusMonths(1).atDay(1);
        LocalDate bucket2Start = month.atDay(8);
        LocalDate bucket3Start = month.atDay(15);
        LocalDate bucket4Start = month.atDay(22);

        MonthlyOrderAnalyticsAggregate aggregate = orderMapper.selectMonthlyAnalytics(
                userUuid,
                toEpochMilli(firstDay),
                toEpochMilli(nextMonthFirstDay),
                toEpochMilli(bucket2Start),
                toEpochMilli(bucket3Start),
                toEpochMilli(bucket4Start),
                RentOrderStatus.codesForMonthlyGroup(MonthlyAnalyticsGroup.ACTIVE),
                RentOrderStatus.FINISHED.getCode(),
                RentOrderStatus.codesForMonthlyGroup(MonthlyAnalyticsGroup.CLOSED)
        );
        if (aggregate == null) {
            throw new RentApiException(500, "月度租赁分析查询失败");
        }

        List<Order> monthOrders = orderMapper.selectList(new QueryWrapper<Order>()
                .select("order_id", "alipay_status", "created_at AS createtime")
                .eq("user_uuid", userUuid)
                .ge("created_at", toEpochMilli(firstDay))
                .lt("created_at", toEpochMilli(nextMonthFirstDay)));
        if (monthOrders == null) {
            throw new RentApiException(500, "月度租赁金额查询失败");
        }
        Map<Integer, Long> paidAmounts = amountService.paidInstallmentAmountCents(monthOrders);
        List<List<Order>> bucketOrders = Arrays.asList(
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (Order order : monthOrders) {
            if (order == null || order.getCreatetime() == null) {
                throw new RentApiException(500, "月度租赁订单创建时间缺失");
            }
            int day = java.time.Instant.ofEpochMilli(order.getCreatetime()).atZone(businessZone).getDayOfMonth();
            bucketOrders.get(day <= 7 ? 0 : day <= 14 ? 1 : day <= 21 ? 2 : 3).add(order);
        }
        long[] bucketRentAmounts = new long[4];
        long confirmedRentAmountCents = 0L;
        for (int i = 0; i < bucketOrders.size(); i++) {
            bucketRentAmounts[i] = amountService.sumRevenueAmountCents(bucketOrders.get(i), paidAmounts);
            confirmedRentAmountCents += bucketRentAmounts[i];
        }

        MonthlyOrderAnalyticsResponse.Summary summary = new MonthlyOrderAnalyticsResponse.Summary(
                aggregate.getOrderCount(),
                aggregate.getActiveCount(),
                aggregate.getFinishedCount(),
                aggregate.getClosedCount(),
                aggregate.getRenewOrderCount(),
                confirmedRentAmountCents
        );

        return new MonthlyOrderAnalyticsResponse(
                month.toString(),
                businessZone.getId(),
                summary,
                Collections.unmodifiableList(Arrays.asList(
                        bucket(firstDay, month.atDay(7), aggregate.getBucket1Count(), bucketRentAmounts[0]),
                        bucket(bucket2Start, month.atDay(14), aggregate.getBucket2Count(), bucketRentAmounts[1]),
                        bucket(bucket3Start, month.atDay(21), aggregate.getBucket3Count(), bucketRentAmounts[2]),
                        bucket(bucket4Start, month.atEndOfMonth(), aggregate.getBucket4Count(), bucketRentAmounts[3])
                ))
        );
    }

    public YearlyOrderAnalyticsResponse getYearlyAnalytics(String requestedYear) {
        String userUuid = currentUserService.requireUserUuid();
        Year year = resolveYear(requestedYear);
        LocalDate start = year.atDay(1);
        LocalDate end = year.plusYears(1).atDay(1);
        long startMs = toEpochMilli(start);
        long endMs = toEpochMilli(end);

        List<YearlyOrderAnalyticsAggregate> rows = orderMapper.selectYearlyAnalytics(
                userUuid, startMs, endMs, businessZone.getId(),
                RentOrderStatus.codesForMonthlyGroup(MonthlyAnalyticsGroup.ACTIVE),
                RentOrderStatus.FINISHED.getCode(),
                RentOrderStatus.codesForMonthlyGroup(MonthlyAnalyticsGroup.CLOSED));
        if (rows == null) {
            throw new RentApiException(500, "年度租赁分析查询失败");
        }

        List<Order> orders = orderMapper.selectList(new QueryWrapper<Order>()
                .select("order_id", "alipay_status", "created_at AS createtime")
                .eq("user_uuid", userUuid).ge("created_at", startMs).lt("created_at", endMs));
        if (orders == null) {
            throw new RentApiException(500, "年度租赁金额查询失败");
        }
        Map<Integer, Long> paidAmounts = amountService.paidInstallmentAmountCents(orders);

        Map<String, YearlyOrderAnalyticsAggregate> rowByMonth = new LinkedHashMap<>();
        rows.forEach(row -> rowByMonth.put(row.getMonth(), row));
        Map<String, List<Order>> ordersByMonth = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order == null || order.getCreatetime() == null) {
                throw new RentApiException(500, "年度租赁订单创建时间缺失");
            }
            String month = YearMonth.from(java.time.Instant.ofEpochMilli(order.getCreatetime())
                    .atZone(businessZone)).toString();
            ordersByMonth.computeIfAbsent(month, ignored -> new ArrayList<>()).add(order);
        }

        List<YearlyOrderAnalyticsResponse.MonthItem> months = new ArrayList<>(12);
        long orderCount = 0, activeCount = 0, finishedCount = 0, closedCount = 0, renewCount = 0, rentAmount = 0;
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            String month = YearMonth.of(year.getValue(), monthNumber).toString();
            YearlyOrderAnalyticsAggregate row = rowByMonth.get(month);
            long monthlyOrders = row == null ? 0 : row.getOrderCount();
            long monthlyRent = amountService.sumRevenueAmountCents(
                    ordersByMonth.getOrDefault(month, Collections.emptyList()), paidAmounts);
            months.add(new YearlyOrderAnalyticsResponse.MonthItem(month, monthlyOrders, monthlyRent));
            orderCount += monthlyOrders;
            rentAmount += monthlyRent;
            if (row != null) {
                activeCount += row.getActiveCount();
                finishedCount += row.getFinishedCount();
                closedCount += row.getClosedCount();
                renewCount += row.getRenewOrderCount();
            }
        }
        List<YearlyOrderAnalyticsResponse.StatusItem> statuses = Arrays.asList(
                new YearlyOrderAnalyticsResponse.StatusItem("ACTIVE", activeCount),
                new YearlyOrderAnalyticsResponse.StatusItem("FINISHED", finishedCount),
                new YearlyOrderAnalyticsResponse.StatusItem("CLOSED", closedCount));
        return new YearlyOrderAnalyticsResponse(year.getValue(), businessZone.getId(), orderCount,
                activeCount, finishedCount, closedCount, renewCount, rentAmount,
                Collections.unmodifiableList(months), Collections.unmodifiableList(statuses));
    }

    private Year resolveYear(String requestedYear) {
        Year current = Year.now(clock.withZone(businessZone));
        if (requestedYear == null) {
            return current;
        }
        if (!YEAR_PATTERN.matcher(requestedYear).matches()) {
            throw new RentApiException(400, "year 格式必须为 YYYY");
        }
        Year year;
        try {
            year = Year.parse(requestedYear);
        } catch (RuntimeException ex) {
            throw new RentApiException(400, "year 格式必须为 YYYY");
        }
        if (year.isAfter(current)) {
            throw new RentApiException(400, "禁止查询未来年份");
        }
        return year;
    }

    private YearMonth resolveMonth(String requestedMonth) {
        if (requestedMonth == null) {
            return YearMonth.now(clock.withZone(businessZone));
        }
        if (!MONTH_PATTERN.matcher(requestedMonth).matches()) {
            throw new RentApiException(400, "month 格式必须为 YYYY-MM");
        }
        try {
            return YearMonth.parse(requestedMonth);
        } catch (RuntimeException ex) {
            throw new RentApiException(400, "month 格式必须为 YYYY-MM");
        }
    }

    private long toEpochMilli(LocalDate date) {
        return date.atStartOfDay(businessZone).toInstant().toEpochMilli();
    }

    private MonthlyOrderAnalyticsResponse.Bucket bucket(
            LocalDate startDate, LocalDate endDate, long orderCount, long rentAmountCents) {
        return new MonthlyOrderAnalyticsResponse.Bucket(
                startDate.toString(), endDate.toString(), orderCount, rentAmountCents);
    }
}

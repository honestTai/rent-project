package com.fly.rent.web.analytics;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Good;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.User;
import com.fly.rent.mapper.GoodMapper;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.UserMapper;
import com.fly.rent.miniapp.cache.MiniappCacheService;
import com.fly.rent.web.analytics.support.AnalyticsValueScoreHelper;
import com.fly.rent.web.support.WebRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.fly.rent.config.AlipayRentConstants.STATUS_CLOSED;
import static com.fly.rent.config.AlipayRentConstants.STATUS_PENDING_CANCLE;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebAnalyticsTopicsService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final GoodMapper goodMapper;
    private final MiniappCacheService cacheService;
    private final AlipayAnalyticsAmountService amountService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final Set<String> ALIPAY_RENTING =
            new HashSet<>(Arrays.asList("DELIVERED", "RECEIVED", "RETURN_DELIVERED"));

    private static final Set<String> ALIPAY_CANCELLED =
            new HashSet<>(Arrays.asList(STATUS_CLOSED, STATUS_PENDING_CANCLE, "CANCELLED", "REFUNDING", "REFUNDED"));

    private static final String[][] PROVINCE_PAIRS = {
            {"新疆维吾尔自治区", "新疆"}, {"内蒙古自治区", "内蒙古"}, {"广西壮族自治区", "广西"},
            {"西藏自治区", "西藏"}, {"宁夏回族自治区", "宁夏"}, {"香港特别行政区", "香港"}, {"澳门特别行政区", "澳门"},
            {"黑龙江省", "黑龙江"}, {"辽宁省", "辽宁"}, {"吉林省", "吉林"}, {"河北省", "河北"}, {"山西省", "山西"},
            {"江苏省", "江苏"}, {"浙江省", "浙江"}, {"安徽省", "安徽"}, {"福建省", "福建"}, {"江西省", "江西"},
            {"山东省", "山东"}, {"河南省", "河南"}, {"湖北省", "湖北"}, {"湖南省", "湖南"}, {"广东省", "广东"},
            {"海南省", "海南"}, {"四川省", "四川"}, {"贵州省", "贵州"}, {"云南省", "云南"}, {"陕西省", "陕西"},
            {"甘肃省", "甘肃"}, {"青海省", "青海"}, {"台湾省", "台湾"},
            {"北京市", "北京"}, {"天津市", "天津"}, {"上海市", "上海"}, {"重庆市", "重庆"}
    };

    public Map<String, Object> subjects(WebRequest req) {
        ZoneId zone = parseZone(req.text("timezone"));
        long[] range = resolveRange(req, zone);
        long startMs = range[0];
        long endMs = range[1];

        if (shouldUseFinishedPeriodCache(endMs)) {
            String cacheKey = buildFinishedPeriodCacheKey("analytics:subjects:v4", zone, startMs, endMs, req.intValue("topN", 20));
            if (Boolean.TRUE.equals(req.bool("forceRefresh"))) {
                Map<String, Object> result = buildSubjects(req, zone, startMs, endMs);
                cacheService.put(cacheKey, result, 7, TimeUnit.DAYS);
                return result;
            }
            return cacheService.getOrLoad(cacheKey, () -> buildSubjects(req, zone, startMs, endMs), false, 7, TimeUnit.DAYS);
        }

        return buildSubjects(req, zone, startMs, endMs);
    }

    private Map<String, Object> buildSubjects(WebRequest req, ZoneId zone, long startMs, long endMs) {
        long[] prevRange = previousRange(startMs, endMs);
        long[] yearRange = yearOnYearRange(startMs, endMs, zone);
        int topN = Math.min(Math.max(req.intValue("topN", 20), 5), 50);

        List<Order> currentOrders = queryOrders(startMs, endMs);
        List<Order> previousOrders = queryOrders(prevRange[0], prevRange[1]);
        List<Order> yearOrders = queryOrders(yearRange[0], yearRange[1]);
        Map<Integer, Long> currentPaidAmountCents = amountService.paidInstallmentAmountCents(currentOrders);
        Map<Integer, Long> previousPaidAmountCents = amountService.paidInstallmentAmountCents(previousOrders);
        Map<Integer, Long> yearPaidAmountCents = amountService.paidInstallmentAmountCents(yearOrders);
        List<User> usersBeforeEnd = queryUsersBefore(endMs);
        List<User> previousUsersBeforeEnd = queryUsersBefore(prevRange[1]);
        List<User> yearUsersBeforeEnd = queryUsersBefore(yearRange[1]);
        List<User> usersInRange = queryUsersInRange(startMs, endMs);
        List<User> previousUsersInRange = queryUsersInRange(prevRange[0], prevRange[1]);
        List<User> yearUsersInRange = queryUsersInRange(yearRange[0], yearRange[1]);
        long listedGoodsCount = queryListedGoodsCount();

        Map<Integer, User> userMap = usersBeforeEnd.stream()
                .filter(user -> user.getUserId() != null)
                .collect(Collectors.toMap(User::getUserId, user -> user, (left, right) -> left, LinkedHashMap::new));

        Map<Integer, UserOrderStat> userOrderStats = buildUserOrderStats(currentOrders, currentPaidAmountCents);
        Map<Integer, UserOrderStat> previousUserOrderStats = buildUserOrderStats(previousOrders, previousPaidAmountCents);
        Map<Integer, AnalyticsValueScoreHelper.ValueProfile> userProfiles = buildUserProfiles(userOrderStats, endMs);
        List<Map<String, Object>> regionRows = buildRegionRows(currentOrders, currentPaidAmountCents);
        List<Map<String, Object>> previousRegionRows = buildRegionRows(previousOrders, previousPaidAmountCents);
        List<Map<String, Object>> yearRegionRows = buildRegionRows(yearOrders, yearPaidAmountCents);
        List<Map<String, Object>> orderTrendRows = buildOrderTrendRows(currentOrders, startMs, endMs, zone, currentPaidAmountCents);
        List<Map<String, Object>> revenueTrendRows = buildRevenueTrendRows(currentOrders, startMs, endMs, zone, currentPaidAmountCents);
        List<Map<String, Object>> registerTrendRows = buildRegisterTrendRows(usersInRange, startMs, endMs, zone);
        List<Map<String, Object>> deviceRows = buildDeviceRows(currentOrders, currentPaidAmountCents);
        List<Map<String, Object>> previousDeviceRows = buildDeviceRows(previousOrders, previousPaidAmountCents);
        List<Map<String, Object>> yearDeviceRows = buildDeviceRows(yearOrders, yearPaidAmountCents);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userPortrait", buildUserPortrait(usersBeforeEnd, previousUsersBeforeEnd, usersInRange, previousUsersInRange, userMap, userOrderStats, previousUserOrderStats, userProfiles, currentOrders, previousOrders, registerTrendRows, topN,
                currentPaidAmountCents, previousPaidAmountCents));
        result.put("regionAnalysis", buildRegionAnalysis(currentOrders, previousOrders, regionRows, previousRegionRows, topN));
        result.put("orderAnalysis", buildOrderAnalysis(currentOrders, previousOrders, orderTrendRows, zone));
        result.put("deviceAnalysis", buildDeviceAnalysis(deviceRows, previousDeviceRows, currentOrders, previousOrders, listedGoodsCount, topN));
        result.put("revenueAnalysis", buildRevenueAnalysis(currentOrders, previousOrders, revenueTrendRows,
                currentPaidAmountCents, previousPaidAmountCents));
        result.put("rentalAnalysis", buildRentalAnalysis(currentOrders, previousOrders, orderTrendRows));
        appendSubjectComparisons(result, currentOrders, previousOrders, yearOrders,
                usersBeforeEnd, previousUsersBeforeEnd, yearUsersBeforeEnd,
                usersInRange, previousUsersInRange, yearUsersInRange,
                regionRows, previousRegionRows, yearRegionRows,
                deviceRows, previousDeviceRows, yearDeviceRows,
                currentPaidAmountCents, previousPaidAmountCents, yearPaidAmountCents);
        result.put("meta", buildMeta(zone, startMs, endMs, currentOrders.size(), usersBeforeEnd.size()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void appendSubjectComparisons(Map<String, Object> result,
                                          List<Order> currentOrders,
                                          List<Order> previousOrders,
                                          List<Order> yearOrders,
                                          List<User> usersBeforeEnd,
                                          List<User> previousUsersBeforeEnd,
                                          List<User> yearUsersBeforeEnd,
                                          List<User> usersInRange,
                                          List<User> previousUsersInRange,
                                          List<User> yearUsersInRange,
                                          List<Map<String, Object>> regionRows,
                                          List<Map<String, Object>> previousRegionRows,
                                          List<Map<String, Object>> yearRegionRows,
                                          List<Map<String, Object>> deviceRows,
                                          List<Map<String, Object>> previousDeviceRows,
                                          List<Map<String, Object>> yearDeviceRows,
                                          Map<Integer, Long> currentPaidAmountCents,
                                          Map<Integer, Long> previousPaidAmountCents,
                                          Map<Integer, Long> yearPaidAmountCents) {
        Map<String, Object> userSummary = summaryOf(result, "userPortrait");
        long authenticatedUsers = usersBeforeEnd.stream().filter(this::isAuthenticatedUser).count();
        long previousAuthenticatedUsers = previousUsersBeforeEnd.stream().filter(this::isAuthenticatedUser).count();
        long yearAuthenticatedUsers = yearUsersBeforeEnd.stream().filter(this::isAuthenticatedUser).count();
        int orderingUsers = buildUserOrderStats(currentOrders, currentPaidAmountCents).size();
        int previousOrderingUsers = buildUserOrderStats(previousOrders, previousPaidAmountCents).size();
        int yearOrderingUsers = buildUserOrderStats(yearOrders, yearPaidAmountCents).size();
        putComparison(userSummary, "totalUsers", "totalUsersChange", usersBeforeEnd.size(), previousUsersBeforeEnd.size(), yearUsersBeforeEnd.size(), false);
        putComparison(userSummary, "newUsers", "newUsersChange", usersInRange.size(), previousUsersInRange.size(), yearUsersInRange.size(), false);
        putComparison(userSummary, "authenticatedRate", "authenticatedRateChange",
                percentage(authenticatedUsers, usersBeforeEnd.size()),
                percentage(previousAuthenticatedUsers, previousUsersBeforeEnd.size()),
                percentage(yearAuthenticatedUsers, yearUsersBeforeEnd.size()),
                true);
        putComparison(userSummary, "orderingUsers", "orderingUsersChange", orderingUsers, previousOrderingUsers, yearOrderingUsers, false);
        putComparison(userSummary, "avgOrderValue", "avgOrderValueChange",
                averageOrderValue(currentOrders, currentPaidAmountCents),
                averageOrderValue(previousOrders, previousPaidAmountCents),
                averageOrderValue(yearOrders, yearPaidAmountCents), false);
        putComparison(userSummary, "averageRentDays", "averageRentDaysChange", averageRentDays(currentOrders), averageRentDays(previousOrders), averageRentDays(yearOrders), false);

        Map<String, Object> regionSummary = summaryOf(result, "regionAnalysis");
        putComparison(regionSummary, "coveredProvinceCount", "coveredProvinceCountChange", regionRows.size(), previousRegionRows.size(), yearRegionRows.size(), false);
        putComparison(regionSummary, "totalOrders", "totalOrdersChange", currentOrders.size(), previousOrders.size(), yearOrders.size(), false);
        putComparison(regionSummary, "totalAmount", "totalAmountChange",
                totalAmount(currentOrders, currentPaidAmountCents),
                totalAmount(previousOrders, previousPaidAmountCents),
                totalAmount(yearOrders, yearPaidAmountCents), false);
        putComparison(regionSummary, "activeUsers", "activeUsersChange", activeUserCount(currentOrders), activeUserCount(previousOrders), activeUserCount(yearOrders), false);
        putComparison(regionSummary, "topProvinceOrders", "topProvinceOrdersChange", topOrderCount(regionRows), topOrderCount(previousRegionRows), topOrderCount(yearRegionRows), false);

        Map<String, Object> orderSummary = summaryOf(result, "orderAnalysis");
        putComparison(orderSummary, "totalOrders", "orderChange", currentOrders.size(), previousOrders.size(), yearOrders.size(), false);
        putComparison(orderSummary, "activeOrders", "activeOrdersChange", activeOrderCount(currentOrders), activeOrderCount(previousOrders), activeOrderCount(yearOrders), false);
        putComparison(orderSummary, "finishedRate", "finishedRateChange", finishedRate(currentOrders), finishedRate(previousOrders), finishedRate(yearOrders), true);

        Map<String, Object> deviceSummary = summaryOf(result, "deviceAnalysis");
        putComparison(deviceSummary, "coveredDeviceCount", "coveredDeviceCountChange", deviceRows.size(), previousDeviceRows.size(), yearDeviceRows.size(), false);
        putComparison(deviceSummary, "topDeviceOrders", "topDeviceOrdersChange", topOrderCount(deviceRows), topOrderCount(previousDeviceRows), topOrderCount(yearDeviceRows), false);
        putComparison(deviceSummary, "totalRentDays", "totalRentDaysChange", totalRentDays(currentOrders), totalRentDays(previousOrders), totalRentDays(yearOrders), false);
        putComparison(deviceSummary, "totalRevenue", "totalRevenueChange",
                totalAmount(currentOrders, currentPaidAmountCents),
                totalAmount(previousOrders, previousPaidAmountCents),
                totalAmount(yearOrders, yearPaidAmountCents), false);

        Map<String, Object> revenueSummary = summaryOf(result, "revenueAnalysis");
        revenueSummary.put("depositAmount", revenueSummary.getOrDefault("totalDeposit", 0));
        revenueSummary.put("paidAmount", revenueSummary.getOrDefault("totalPaid", 0));
        putComparison(revenueSummary, "totalRevenue", "revenueChange",
                totalAmount(currentOrders, currentPaidAmountCents),
                totalAmount(previousOrders, previousPaidAmountCents),
                totalAmount(yearOrders, yearPaidAmountCents), false);
        putComparison(revenueSummary, "totalDeposit", "depositChange", totalDeposit(currentOrders), totalDeposit(previousOrders), totalDeposit(yearOrders), false);
        putComparison(revenueSummary, "totalPaid", "paidChange",
                totalPaid(currentOrders, currentPaidAmountCents),
                totalPaid(previousOrders, previousPaidAmountCents),
                totalPaid(yearOrders, yearPaidAmountCents),
                false);
        putComparison(revenueSummary, "avgOrderValue", "avgOrderValueChange",
                averageOrderValue(currentOrders, currentPaidAmountCents),
                averageOrderValue(previousOrders, previousPaidAmountCents),
                averageOrderValue(yearOrders, yearPaidAmountCents), false);
        aliasComparison(revenueSummary, "totalDeposit", "depositAmount");
        aliasComparison(revenueSummary, "totalPaid", "paidAmount");

        Map<String, Object> rentalSummary = summaryOf(result, "rentalAnalysis");
        putComparison(rentalSummary, "totalRentDays", "totalRentDaysChange", totalRentDays(currentOrders), totalRentDays(previousOrders), totalRentDays(yearOrders), false);
        putComparison(rentalSummary, "averageRentDays", "averageRentDaysChange", averageRentDays(currentOrders), averageRentDays(previousOrders), averageRentDays(yearOrders), false);
        putComparison(rentalSummary, "activeOrders", "activeOrdersChange", activeOrderCount(currentOrders), activeOrderCount(previousOrders), activeOrderCount(yearOrders), false);
        putComparison(rentalSummary, "finishedRate", "finishedRateChange", finishedRate(currentOrders), finishedRate(previousOrders), finishedRate(yearOrders), true);
        putComparison(rentalSummary, "overdueRate", "overdueRateChange", overdueRate(currentOrders), overdueRate(previousOrders), overdueRate(yearOrders), true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> summaryOf(Map<String, Object> result, String sectionKey) {
        Object section = result.get(sectionKey);
        if (!(section instanceof Map)) {
            return new LinkedHashMap<>();
        }
        Object summary = ((Map<String, Object>) section).get("summary");
        if (!(summary instanceof Map)) {
            Map<String, Object> newSummary = new LinkedHashMap<>();
            ((Map<String, Object>) section).put("summary", newSummary);
            return newSummary;
        }
        return (Map<String, Object>) summary;
    }

    private void putComparison(Map<String, Object> summary,
                               String metricKey,
                               String oldChangeKey,
                               double current,
                               double previous,
                               double year,
                               boolean percentagePoint) {
        double mom = summary.containsKey(oldChangeKey)
                ? numberValue(summary.get(oldChangeKey))
                : comparisonValue(current, previous, percentagePoint);
        summary.put(metricKey + "MoM", round1(mom));
        summary.put(metricKey + "YoY", round1(comparisonValue(current, year, percentagePoint)));
        summary.put(oldChangeKey + "MoM", summary.get(metricKey + "MoM"));
        summary.put(oldChangeKey + "YoY", summary.get(metricKey + "YoY"));
    }

    private void aliasComparison(Map<String, Object> summary, String sourceKey, String aliasKey) {
        summary.put(aliasKey + "MoM", summary.getOrDefault(sourceKey + "MoM", 0));
        summary.put(aliasKey + "YoY", summary.getOrDefault(sourceKey + "YoY", 0));
    }

    private double comparisonValue(double current, double baseline, boolean percentagePoint) {
        if (percentagePoint) {
            return current - baseline;
        }
        return changeRate(current, baseline);
    }

    private double totalAmount(List<Order> orders) {
        return totalAmount(orders, Collections.emptyMap());
    }

    private double totalAmount(List<Order> orders, Map<Integer, Long> paidAmountCents) {
        return round2(amountService.sumRevenueAmountCents(orders, paidAmountCents) / 100.0);
    }

    private double totalDeposit(List<Order> orders) {
        return round2(orders.stream()
                .filter(amountService::isRevenueEligible)
                .mapToLong(order -> safeInt(order.getOrderDeposit()))
                .sum() / 100.0);
    }

    private double totalPaid(List<Order> orders, Map<Integer, Long> paidAmountCents) {
        return round2(amountService.sumRevenueAmountCents(orders, paidAmountCents) / 100.0);
    }

    private long totalRentDays(List<Order> orders) {
        return orders.stream()
                .filter(amountService::isRevenueEligible)
                .mapToLong(order -> safeInt(order.getOrderKeep()))
                .sum();
    }

    private double averageOrderValue(List<Order> orders, Map<Integer, Long> paidAmountCents) {
        if (orders == null || orders.isEmpty()) {
            return 0;
        }
        return round2(totalAmount(orders, paidAmountCents) / orders.size());
    }

    private double averageRentDays(List<Order> orders) {
        return orders.stream()
                .filter(amountService::isRevenueEligible)
                .filter(order -> safeInt(order.getOrderKeep()) > 0)
                .mapToInt(order -> safeInt(order.getOrderKeep()))
                .average()
                .orElse(0);
    }

    private long activeOrderCount(List<Order> orders) {
        return orders.stream()
                .filter(order -> ALIPAY_RENTING.contains(normalizeAlipayStatus(order.getAlipayStatus())))
                .count();
    }

    private double finishedRate(List<Order> orders) {
        long finishedOrders = orders.stream()
                .filter(order -> isFinishedStatus(normalizeAlipayStatus(order.getAlipayStatus())))
                .count();
        return percentage(finishedOrders, orders.size());
    }

    private double overdueRate(List<Order> orders) {
        long activeOrders = activeOrderCount(orders);
        long overdueOrders = orders.stream().filter(this::isOverdueOrder).count();
        return percentage(overdueOrders, activeOrders);
    }

    private int activeUserCount(List<Order> orders) {
        return orders.stream().map(Order::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()).size();
    }

    private double topOrderCount(List<Map<String, Object>> rows) {
        return rows == null || rows.isEmpty() ? 0 : numberValue(rows.get(0).getOrDefault("orderCount", 0));
    }

    private boolean shouldUseFinishedPeriodCache(long endMs) {
        return endMs < System.currentTimeMillis();
    }

    private String buildFinishedPeriodCacheKey(String prefix, ZoneId zone, long startMs, long endMs, int topN) {
        LocalDate start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate();
        return prefix + ":" + zone.getId() + ":" + start.format(DATE_FMT) + ":" + end.format(DATE_FMT) + ":topN:" + topN;
    }


    private List<Order> queryOrders(long startMs, long endMs) {
        QueryWrapper<Order> ew = new QueryWrapper<>();
        ew.ge("created_at", startMs);
        ew.le("created_at", endMs);
        return orderMapper.selectList(ew);
    }

    private List<User> queryUsersBefore(long endMs) {
        QueryWrapper<User> ew = new QueryWrapper<>();
        ew.le("created_at", endMs);
        return userMapper.selectList(ew);
    }

    private List<User> queryUsersInRange(long startMs, long endMs) {
        QueryWrapper<User> ew = new QueryWrapper<>();
        ew.ge("created_at", startMs);
        ew.le("created_at", endMs);
        return userMapper.selectList(ew);
    }

    private long queryListedGoodsCount() {
        Long count = goodMapper.selectCount(new QueryWrapper<Good>());
        return count == null ? 0 : count;
    }

    private Map<String, Object> buildUserPortrait(List<User> usersBeforeEnd,
                                                  List<User> previousUsersBeforeEnd,
                                                  List<User> usersInRange,
                                                  List<User> previousUsersInRange,
                                                  Map<Integer, User> userMap,
                                                  Map<Integer, UserOrderStat> userOrderStats,
                                                  Map<Integer, UserOrderStat> previousUserOrderStats,
                                                  Map<Integer, AnalyticsValueScoreHelper.ValueProfile> userProfiles,
                                                  List<Order> currentOrders,
                                                  List<Order> previousOrders,
                                                  List<Map<String, Object>> registerTrendRows,
                                                  int topN,
                                                  Map<Integer, Long> currentPaidAmountCents,
                                                  Map<Integer, Long> previousPaidAmountCents) {
        Map<String, Object> result = new LinkedHashMap<>();
        long totalUsers = usersBeforeEnd.size();
        long previousTotalUsers = previousUsersBeforeEnd.size();
        long authenticatedUsers = usersBeforeEnd.stream().filter(this::isAuthenticatedUser).count();
        long previousAuthenticatedUsers = previousUsersBeforeEnd.stream().filter(this::isAuthenticatedUser).count();
        long orderingUsers = userOrderStats.size();
        long previousOrderingUsers = previousUserOrderStats.size();
        long repurchaseUsers = userOrderStats.values().stream().filter(stat -> stat.orderCount >= 2).count();
        long previousRepurchaseUsers = previousUserOrderStats.values().stream().filter(stat -> stat.orderCount >= 2).count();
        double avgOrderValue = currentOrders.isEmpty()
                ? 0
                : round2(totalAmount(currentOrders, currentPaidAmountCents) / currentOrders.size());
        double previousAvgOrderValue = previousOrders.isEmpty()
                ? 0
                : round2(totalAmount(previousOrders, previousPaidAmountCents) / previousOrders.size());
        double averageRentDays = currentOrders.stream()
                .filter(amountService::isRevenueEligible)
                .filter(order -> safeInt(order.getOrderKeep()) > 0)
                .mapToInt(order -> safeInt(order.getOrderKeep()))
                .average()
                .orElse(0);
        double previousAverageRentDays = previousOrders.stream()
                .filter(amountService::isRevenueEligible)
                .filter(order -> safeInt(order.getOrderKeep()) > 0)
                .mapToInt(order -> safeInt(order.getOrderKeep()))
                .average()
                .orElse(0);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalUsers", totalUsers);
        summary.put("newUsers", usersInRange.size());
        summary.put("authenticatedUsers", authenticatedUsers);
        summary.put("authenticatedRate", percentage(authenticatedUsers, totalUsers));
        summary.put("orderingUsers", orderingUsers);
        summary.put("repurchaseUsers", repurchaseUsers);
        summary.put("repurchaseRate", percentage(repurchaseUsers, orderingUsers));
        summary.put("avgOrderValue", avgOrderValue);
        summary.put("averageRentDays", round1(averageRentDays));
        summary.put("totalUsersChange", changeRate(totalUsers, previousTotalUsers));
        summary.put("newUsersChange", changeRate(usersInRange.size(), previousUsersInRange.size()));
        summary.put("authenticatedRateChange", percentage(authenticatedUsers, totalUsers) - percentage(previousAuthenticatedUsers, previousTotalUsers));
        summary.put("orderingUsersChange", changeRate(orderingUsers, previousOrderingUsers));
        summary.put("repurchaseRateChange", percentage(repurchaseUsers, orderingUsers) - percentage(previousRepurchaseUsers, previousOrderingUsers));
        summary.put("avgOrderValueChange", changeRate(avgOrderValue, previousAvgOrderValue));
        summary.put("averageRentDaysChange", changeRate(averageRentDays, previousAverageRentDays));
        result.put("summary", summary);

        List<Map<String, Object>> authRows = new ArrayList<>();
        authRows.add(buildSimpleRow("label", "已实名", "value", authenticatedUsers));
        authRows.add(buildSimpleRow("label", "未实名", "value", Math.max(totalUsers - authenticatedUsers, 0)));
        result.put("authRows", authRows);
        result.put("registerRows", registerTrendRows);

        Map<String, ProvinceUserStat> provinceMap = new LinkedHashMap<>();
        usersBeforeEnd.forEach(user -> {
            String province = resolveUserProvince(user, userOrderStats);
            if (!hasText(province)) {
                province = "未知";
            }
            ProvinceUserStat stat = provinceMap.computeIfAbsent(province, key -> new ProvinceUserStat());
            stat.province = province;
            stat.userCount++;
            if (isAuthenticatedUser(user)) {
                stat.authenticatedUsers++;
            }
            UserOrderStat orderStat = user.getUserId() == null ? null : userOrderStats.get(user.getUserId());
            if (orderStat != null) {
                stat.orderUserCount++;
                stat.totalAmount += orderStat.totalAmount;
            }
        });
        List<Map<String, Object>> provinceRows = provinceMap.values().stream()
                .sorted(Comparator.comparingLong((ProvinceUserStat stat) -> stat.orderUserCount).reversed()
                        .thenComparingDouble(stat -> stat.totalAmount).reversed())
                .map(stat -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", stat.province);
                    row.put("userCount", stat.userCount);
                    row.put("authenticatedUsers", stat.authenticatedUsers);
                    row.put("orderUserCount", stat.orderUserCount);
                    row.put("totalAmount", round2(stat.totalAmount));
                    return row;
                })
                .collect(Collectors.toList());
        result.put("provinceRows", provinceRows);

        List<Map<String, Object>> valueSegmentRows = buildValueSegmentRows(userOrderStats, userProfiles);
        result.put("valueSegmentRows", valueSegmentRows);
        result.put("rentPreferenceRows", buildRentPreferenceRows(currentOrders));

        List<Map<String, Object>> topCustomerRows = userOrderStats.entrySet().stream()
                .sorted((left, right) -> Double.compare(
                        profileScore(userProfiles.get(right.getKey())),
                        profileScore(userProfiles.get(left.getKey()))
                ))
                .limit(topN)
                .map(entry -> {
                    Integer userId = entry.getKey();
                    UserOrderStat stat = entry.getValue();
                    User user = userMap.get(userId);
                    AnalyticsValueScoreHelper.ValueProfile profile = userProfiles.get(userId);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("userId", userId);
                    row.put("userName", resolveUserName(user, stat, userId));
                    row.put("province", resolveProvinceText(user, stat.province));
                    row.put("authenticated", user != null && isAuthenticatedUser(user));
                    row.put("orderCount", stat.orderCount);
                    row.put("totalAmount", round2(stat.totalAmount));
                    row.put("avgOrderValue", round2(stat.totalAmount / Math.max(stat.orderCount, 1)));
                    row.put("avgRentDays", round1(stat.rentCount > 0 ? stat.totalRentDays / stat.rentCount : 0));
                    row.put("valueScore", round2(profileScore(profile)));
                    row.put("valueLevel", profile == null ? "D" : profile.getLevel());
                    row.put("valueTag", profile == null ? "普通用户" : profile.getTag());
                    row.put("factorSummary", profile == null ? "暂无模型结果" : profile.getFactorSummary());
                    return row;
                })
                .collect(Collectors.toList());
        result.put("topCustomerRows", topCustomerRows);

        return result;
    }

    private Map<String, Object> buildRegionAnalysis(List<Order> currentOrders,
                                                    List<Order> previousOrders,
                                                    List<Map<String, Object>> regionRows,
                                                    List<Map<String, Object>> previousRegionRows,
                                                    int topN) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> topProvince = regionRows.isEmpty() ? Collections.emptyMap() : regionRows.get(0);
        int previousActiveUsers = previousOrders.stream().map(Order::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()).size();
        summary.put("coveredProvinceCount", regionRows.size());
        summary.put("totalOrders", currentOrders.size());
        summary.put("totalAmount", round2(regionRows.stream().mapToDouble(row -> numberValue(row.get("totalAmount"))).sum()));
        summary.put("activeUsers", currentOrders.stream().map(Order::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()).size());
        summary.put("topProvince", topProvince.getOrDefault("name", "-"));
        summary.put("topProvinceOrders", topProvince.getOrDefault("orderCount", 0));
        summary.put("coveredProvinceCountChange", changeRate(regionRows.size(), previousRegionRows.size()));
        summary.put("totalOrdersChange", changeRate(currentOrders.size(), previousOrders.size()));
        summary.put("totalAmountChange", changeRate(
                round2(regionRows.stream().mapToDouble(row -> numberValue(row.get("totalAmount"))).sum()),
                round2(previousRegionRows.stream().mapToDouble(row -> numberValue(row.get("totalAmount"))).sum())
        ));
        summary.put("activeUsersChange", changeRate(summary.get("activeUsers") instanceof Number ? ((Number) summary.get("activeUsers")).doubleValue() : 0, previousActiveUsers));
        summary.put("topProvinceOrdersChange", changeRate(numberValue(topProvince.getOrDefault("orderCount", 0)),
                previousRegionRows.isEmpty() ? 0 : numberValue(previousRegionRows.get(0).getOrDefault("orderCount", 0))));
        result.put("summary", summary);
        result.put("provinceRows", regionRows);
        result.put("topOrderRows", regionRows.stream().limit(topN).collect(Collectors.toList()));
        result.put("topRevenueRows", regionRows.stream()
                .sorted((left, right) -> Double.compare(numberValue(right.get("totalAmount")), numberValue(left.get("totalAmount"))))
                .limit(topN)
                .collect(Collectors.toList()));
        return result;
    }

    private Map<String, Object> buildOrderAnalysis(List<Order> currentOrders,
                                                   List<Order> previousOrders,
                                                   List<Map<String, Object>> orderTrendRows,
                                                   ZoneId zone) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Long> statusMap = buildStatusCountMap(currentOrders);
        Map<String, Long> hourMap = buildHourCountMap(currentOrders, zone);
        Map<String, Long> weekdayMap = buildWeekdayCountMap(currentOrders, zone);
        long activeOrders = currentOrders.stream()
                .filter(order -> ALIPAY_RENTING.contains(normalizeAlipayStatus(order.getAlipayStatus())))
                .count();
        long finishedOrders = currentOrders.stream()
                .filter(order -> isFinishedStatus(normalizeAlipayStatus(order.getAlipayStatus())))
                .count();
        long cancelledOrders = currentOrders.stream()
                .filter(order -> ALIPAY_CANCELLED.contains(normalizeAlipayStatus(order.getAlipayStatus())))
                .count();
        Map<String, Object> peakHour = hourMap.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(entry -> buildSimpleRow("label", entry.getKey(), "value", entry.getValue()))
                .orElse(buildSimpleRow("label", "-", "value", 0L));
        Map<String, Object> peakWeekday = weekdayMap.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(entry -> buildSimpleRow("label", entry.getKey(), "value", entry.getValue()))
                .orElse(buildSimpleRow("label", "-", "value", 0L));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOrders", currentOrders.size());
        summary.put("activeOrders", activeOrders);
        summary.put("finishedOrders", finishedOrders);
        summary.put("finishedRate", percentage(finishedOrders, currentOrders.size()));
        summary.put("cancelledOrders", cancelledOrders);
        summary.put("cancelledRate", percentage(cancelledOrders, currentOrders.size()));
        summary.put("orderChange", changeRate(currentOrders.size(), previousOrders.size()));
        summary.put("peakHour", peakHour);
        summary.put("peakWeekday", peakWeekday);

        result.put("summary", summary);
        result.put("trendRows", orderTrendRows);
        result.put("statusRows", statusMap.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", translateOrderStatus(entry.getKey()));
                    row.put("value", entry.getValue());
                    return row;
                })
                .collect(Collectors.toList()));
        result.put("hourRows", hourMap.entrySet().stream()
                .map(entry -> buildSimpleRow("label", entry.getKey(), "value", entry.getValue()))
                .collect(Collectors.toList()));
        result.put("weekdayRows", weekdayMap.entrySet().stream()
                .map(entry -> buildSimpleRow("label", entry.getKey(), "value", entry.getValue()))
                .collect(Collectors.toList()));
        return result;
    }

    private Map<String, Object> buildDeviceAnalysis(List<Map<String, Object>> deviceRows,
                                                    List<Map<String, Object>> previousDeviceRows,
                                                    List<Order> currentOrders,
                                                    List<Order> previousOrders,
                                                    long listedGoodsCount,
                                                    int topN) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> valueRankRows = deviceRows.stream()
                .sorted((left, right) -> Double.compare(numberValue(right.get("valueScore")), numberValue(left.get("valueScore"))))
                .collect(Collectors.toList());
        Map<String, Object> topDevice = valueRankRows.isEmpty() ? Collections.emptyMap() : valueRankRows.get(0);
        Map<String, Object> previousTopDevice = previousDeviceRows.isEmpty() ? Collections.emptyMap() : previousDeviceRows.get(0);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("coveredDeviceCount", deviceRows.size());
        summary.put("listedDeviceCount", listedGoodsCount);
        summary.put("topDevice", topDevice.getOrDefault("name", "-"));
        summary.put("topDeviceOrders", topDevice.getOrDefault("orderCount", 0));
        summary.put("topDeviceValueScore", round2(numberValue(topDevice.getOrDefault("valueScore", 0D))));
        summary.put("totalRentDays", deviceRows.stream().mapToLong(row -> ((Number) row.getOrDefault("totalRentDays", 0)).longValue()).sum());
        summary.put("totalRevenue", round2(deviceRows.stream().mapToDouble(row -> numberValue(row.get("totalAmount"))).sum()));
        summary.put("coveredDeviceCountChange", changeRate(deviceRows.size(), previousDeviceRows.size()));
        summary.put("listedDeviceCountChange", 0);
        summary.put("topDeviceOrdersChange", changeRate(numberValue(topDevice.getOrDefault("orderCount", 0)),
                numberValue(previousTopDevice.getOrDefault("orderCount", 0))));
        summary.put("totalRentDaysChange", changeRate(
                deviceRows.stream().mapToLong(row -> ((Number) row.getOrDefault("totalRentDays", 0)).longValue()).sum(),
                previousDeviceRows.stream().mapToLong(row -> ((Number) row.getOrDefault("totalRentDays", 0)).longValue()).sum()
        ));
        summary.put("totalRevenueChange", changeRate(
                round2(deviceRows.stream().mapToDouble(row -> numberValue(row.get("totalAmount"))).sum()),
                round2(previousDeviceRows.stream().mapToDouble(row -> numberValue(row.get("totalAmount"))).sum())
        ));
        result.put("summary", summary);
        result.put("deviceRows", deviceRows);
        result.put("topOrderRows", valueRankRows.stream().limit(topN).collect(Collectors.toList()));
        result.put("topRevenueRows", deviceRows.stream()
                .sorted((left, right) -> Double.compare(numberValue(right.get("totalAmount")), numberValue(left.get("totalAmount"))))
                .limit(topN)
                .collect(Collectors.toList()));
        result.put("topRentDayRows", deviceRows.stream()
                .sorted((left, right) -> Double.compare(numberValue(right.get("totalRentDays")), numberValue(left.get("totalRentDays"))))
                .limit(topN)
                .collect(Collectors.toList()));
        return result;
    }

    private Map<String, Object> buildRevenueAnalysis(List<Order> currentOrders,
                                                     List<Order> previousOrders,
                                                     List<Map<String, Object>> revenueTrendRows,
                                                     Map<Integer, Long> currentPaidAmountCents,
                                                     Map<Integer, Long> previousPaidAmountCents) {
        Map<String, Object> result = new LinkedHashMap<>();
        double totalRevenue = totalAmount(currentOrders, currentPaidAmountCents);
        double totalDeposit = totalDeposit(currentOrders);
        double totalPaid = totalPaid(currentOrders, currentPaidAmountCents);
        double previousRevenue = totalAmount(previousOrders, previousPaidAmountCents);
        double previousPaid = totalPaid(previousOrders, previousPaidAmountCents);
        double previousDeposit = totalDeposit(previousOrders);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalDeposit", totalDeposit);
        summary.put("totalPaid", totalPaid);
        summary.put("avgOrderValue", currentOrders.isEmpty() ? 0 : round2(totalRevenue / currentOrders.size()));
        summary.put("revenueChange", changeRate(totalRevenue, previousRevenue));
        summary.put("paidChange", changeRate(totalPaid, previousPaid));
        summary.put("depositChange", changeRate(totalDeposit, previousDeposit));
        result.put("summary", summary);
        result.put("trendRows", revenueTrendRows);

        List<Map<String, Object>> compositionRows = new ArrayList<>();
        compositionRows.add(buildSimpleRow("label", "总收入", "value", totalRevenue));
        compositionRows.add(buildSimpleRow("label", "押金规模", "value", totalDeposit));
        compositionRows.add(buildSimpleRow("label", "实付金额", "value", totalPaid));
        result.put("compositionRows", compositionRows);
        return result;
    }

    private Map<String, Object> buildRentalAnalysis(List<Order> currentOrders,
                                                    List<Order> previousOrders,
                                                    List<Map<String, Object>> orderTrendRows) {
        Map<String, Object> result = new LinkedHashMap<>();
        long totalRentDays = totalRentDays(currentOrders);
        long previousTotalRentDays = totalRentDays(previousOrders);
        long activeOrders = currentOrders.stream()
                .filter(order -> ALIPAY_RENTING.contains(normalizeAlipayStatus(order.getAlipayStatus())))
                .count();
        long previousActiveOrders = previousOrders.stream()
                .filter(order -> ALIPAY_RENTING.contains(normalizeAlipayStatus(order.getAlipayStatus())))
                .count();
        long finishedOrders = currentOrders.stream()
                .filter(order -> isFinishedStatus(normalizeAlipayStatus(order.getAlipayStatus())))
                .count();
        long previousFinishedOrders = previousOrders.stream()
                .filter(order -> isFinishedStatus(normalizeAlipayStatus(order.getAlipayStatus())))
                .count();
        long overdueOrders = currentOrders.stream()
                .filter(this::isOverdueOrder)
                .count();
        long previousOverdueOrders = previousOrders.stream()
                .filter(this::isOverdueOrder)
                .count();
        double averageRentDays = currentOrders.stream()
                .filter(amountService::isRevenueEligible)
                .filter(order -> safeInt(order.getOrderKeep()) > 0)
                .mapToInt(order -> safeInt(order.getOrderKeep()))
                .average()
                .orElse(0);
        double previousAverageRentDays = previousOrders.stream()
                .filter(amountService::isRevenueEligible)
                .filter(order -> safeInt(order.getOrderKeep()) > 0)
                .mapToInt(order -> safeInt(order.getOrderKeep()))
                .average()
                .orElse(0);

        Map<String, Long> durationMap = buildRentalDurationMap(currentOrders);
        List<Map<String, Object>> performanceRows = new ArrayList<>();
        performanceRows.add(buildSimpleRow("label", "进行中", "value", activeOrders));
        performanceRows.add(buildSimpleRow("label", "已完结", "value", finishedOrders));
        performanceRows.add(buildSimpleRow("label", "已取消", "value", currentOrders.stream()
                .filter(order -> ALIPAY_CANCELLED.contains(normalizeAlipayStatus(order.getAlipayStatus())))
                .count()));
        performanceRows.add(buildSimpleRow("label", "已逾期", "value", overdueOrders));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRentDays", totalRentDays);
        summary.put("averageRentDays", round1(averageRentDays));
        summary.put("activeOrders", activeOrders);
        summary.put("finishedRate", percentage(finishedOrders, currentOrders.size()));
        summary.put("overdueOrders", overdueOrders);
        summary.put("overdueRate", percentage(overdueOrders, activeOrders));
        summary.put("totalRentDaysChange", changeRate(totalRentDays, previousTotalRentDays));
        summary.put("averageRentDaysChange", changeRate(averageRentDays, previousAverageRentDays));
        summary.put("activeOrdersChange", changeRate(activeOrders, previousActiveOrders));
        summary.put("finishedRateChange", percentage(finishedOrders, currentOrders.size()) - percentage(previousFinishedOrders, previousOrders.size()));
        summary.put("overdueRateChange", percentage(overdueOrders, activeOrders) - percentage(previousOverdueOrders, previousActiveOrders));
        result.put("summary", summary);
        result.put("trendRows", orderTrendRows.stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("date", row.get("date"));
                    item.put("totalRentDays", row.get("totalRentDays"));
                    item.put("averageRentDays", row.get("averageRentDays"));
                    return item;
                })
                .collect(Collectors.toList()));
        List<Map<String, Object>> durationRows = durationMap.entrySet().stream()
                .map(entry -> buildSimpleRow("label", entry.getKey(), "value", entry.getValue()))
                .collect(Collectors.toList());
        result.put("durationRows", durationRows);
        result.put("rentPreferenceRows", durationRows);
        result.put("performanceRows", performanceRows);
        return result;
    }

    private List<Map<String, Object>> buildRegionRows(List<Order> orders, Map<Integer, Long> paidAmountCents) {
        Map<String, RegionStat> regionMap = new LinkedHashMap<>();
        orders.forEach(order -> {
            String province = extractProvince(order.getAddr());
            if (!hasText(province)) {
                return;
            }
            RegionStat stat = regionMap.computeIfAbsent(province, key -> new RegionStat());
            stat.name = province;
            stat.orderCount++;
            stat.totalAmount += amountService.revenueAmountCents(order, paidAmountCents) / 100.0;
            if (amountService.isRevenueEligible(order)) {
                stat.totalRentDays += safeInt(order.getOrderKeep());
            }
            if (order.getUserId() != null) {
                stat.userIds.add(order.getUserId());
            }
        });
        return regionMap.values().stream()
                .sorted(Comparator.comparingLong((RegionStat stat) -> stat.orderCount).reversed()
                        .thenComparingDouble(stat -> stat.totalAmount).reversed())
                .map(stat -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", stat.name);
                    row.put("orderCount", stat.orderCount);
                    row.put("totalAmount", round2(stat.totalAmount));
                    row.put("uniqueUsers", stat.userIds.size());
                    row.put("avgOrderValue", round2(stat.orderCount > 0 ? stat.totalAmount / stat.orderCount : 0));
                    row.put("averageRentDays", round1(stat.orderCount > 0 ? stat.totalRentDays / (double) stat.orderCount : 0));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildOrderTrendRows(List<Order> orders, long startMs, long endMs, ZoneId zone,
                                                          Map<Integer, Long> paidAmountCents) {
        String granularity = resolveGranularity(startMs, endMs);
        Map<String, double[]> bucketMap = new TreeMap<>();
        orders.forEach(order -> {
            String key = bucketKey(order.getCreatetime(), zone, granularity);
            double[] bucket = bucketMap.computeIfAbsent(key, item -> new double[]{0, 0, 0, 0});
            bucket[0] += 1;
            bucket[1] += amountService.revenueAmountCents(order, paidAmountCents) / 100.0;
            int rentDays = safeInt(order.getOrderKeep());
            if (rentDays > 0 && amountService.isRevenueEligible(order)) {
                bucket[2] += rentDays;
                bucket[3] += 1;
            }
        });
        fillMissingBuckets(bucketMap, startMs, endMs, zone, granularity, 4);
        return bucketMap.entrySet().stream()
                .map(entry -> {
                    double[] value = entry.getValue();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", entry.getKey());
                    row.put("orderCount", (long) value[0]);
                    row.put("totalAmount", round2(value[1]));
                    row.put("totalRentDays", (long) value[2]);
                    row.put("averageRentDays", round1(value[3] > 0 ? value[2] / value[3] : 0));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildRevenueTrendRows(List<Order> orders,
                                                            long startMs,
                                                            long endMs,
                                                            ZoneId zone,
                                                            Map<Integer, Long> paidAmountCents) {
        String granularity = resolveGranularity(startMs, endMs);
        Map<String, double[]> bucketMap = new TreeMap<>();
        orders.forEach(order -> {
            String key = bucketKey(order.getCreatetime(), zone, granularity);
            double[] bucket = bucketMap.computeIfAbsent(key, item -> new double[]{0, 0, 0});
            bucket[0] += amountService.revenueAmountCents(order, paidAmountCents) / 100.0;
            if (amountService.isRevenueEligible(order)) {
                bucket[1] += safeInt(order.getOrderDeposit()) / 100.0;
            }
            bucket[2] += amountService.revenueAmountCents(order, paidAmountCents) / 100.0;
        });
        fillMissingBuckets(bucketMap, startMs, endMs, zone, granularity, 3);
        return bucketMap.entrySet().stream()
                .map(entry -> {
                    double[] value = entry.getValue();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", entry.getKey());
                    row.put("totalRevenue", round2(value[0]));
                    row.put("totalDeposit", round2(value[1]));
                    row.put("totalPaid", round2(value[2]));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildRegisterTrendRows(List<User> users, long startMs, long endMs, ZoneId zone) {
        String granularity = resolveGranularity(startMs, endMs);
        Map<String, double[]> bucketMap = new TreeMap<>();
        users.forEach(user -> {
            String key = bucketKey(user.getCreatetime(), zone, granularity);
            double[] bucket = bucketMap.computeIfAbsent(key, item -> new double[]{0});
            bucket[0] += 1;
        });
        fillMissingBuckets(bucketMap, startMs, endMs, zone, granularity, 1);
        return bucketMap.entrySet().stream()
                .map(entry -> buildSimpleRow("date", entry.getKey(), "count", (long) entry.getValue()[0]))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildDeviceRows(List<Order> orders, Map<Integer, Long> paidAmountCents) {
        Map<String, DeviceStat> deviceMap = new LinkedHashMap<>();
        orders.forEach(order -> {
            String key = hasText(order.getGoodTitle()) ? order.getGoodTitle().trim()
                    : (order.getGoodId() == null ? "未知设备" : "设备#" + order.getGoodId());
            DeviceStat stat = deviceMap.computeIfAbsent(key, item -> new DeviceStat());
            stat.name = key;
            stat.orderCount++;
            stat.totalAmount += amountService.revenueAmountCents(order, paidAmountCents) / 100.0;
            if (amountService.isRevenueEligible(order)) {
                stat.totalRentDays += safeInt(order.getOrderKeep());
            }
        });
        DeviceValueContext deviceContext = buildDeviceContext(deviceMap.values());
        deviceMap.values().forEach(stat -> applyDeviceProfile(stat, deviceContext, orders.size()));
        int totalOrders = orders.size();
        return deviceMap.values().stream()
                .sorted(Comparator.comparingDouble((DeviceStat stat) -> stat.valueScore).reversed()
                        .thenComparingLong(stat -> stat.orderCount).reversed())
                .map(stat -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", stat.name);
                    row.put("orderCount", stat.orderCount);
                    row.put("totalAmount", round2(stat.totalAmount));
                    row.put("totalRentDays", stat.totalRentDays);
                    row.put("averageRentDays", round1(stat.orderCount > 0 ? stat.totalRentDays / (double) stat.orderCount : 0));
                    row.put("orderShare", totalOrders > 0 ? round1(stat.orderCount * 100.0 / totalOrders) : 0);
                    row.put("valueScore", round2(stat.valueScore));
                    row.put("valueLevel", stat.valueLevel);
                    row.put("valueTag", stat.valueTag);
                    row.put("factorSummary", stat.factorSummary);
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildValueSegmentRows(Map<Integer, UserOrderStat> userOrderStats,
                                                            Map<Integer, AnalyticsValueScoreHelper.ValueProfile> userProfiles) {
        Map<String, SegmentStat> segmentMap = new LinkedHashMap<>();
        segmentMap.put("高价值用户", new SegmentStat("高价值用户"));
        segmentMap.put("成长型用户", new SegmentStat("成长型用户"));
        segmentMap.put("流失风险用户", new SegmentStat("流失风险用户"));
        segmentMap.put("潜力新客", new SegmentStat("潜力新客"));
        segmentMap.put("普通用户", new SegmentStat("普通用户"));
        userOrderStats.forEach((userId, stat) -> {
            AnalyticsValueScoreHelper.ValueProfile profile = userProfiles.get(userId);
            String label = profile == null ? "普通用户" : profile.getTag();
            SegmentStat segment = segmentMap.get(label);
            segment.userCount++;
            segment.orderCount += stat.orderCount;
            segment.totalAmount += stat.totalAmount;
            segment.totalScore += profileScore(profile);
        });
        return segmentMap.values().stream()
                .filter(segment -> segment.userCount > 0)
                .map(segment -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", segment.label);
                    row.put("userCount", segment.userCount);
                    row.put("orderCount", segment.orderCount);
                    row.put("totalAmount", round2(segment.totalAmount));
                    row.put("avgScore", segment.userCount <= 0 ? 0D : round2(segment.totalScore / segment.userCount));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildRentPreferenceRows(List<Order> currentOrders) {
        Map<String, PreferenceStat> preferenceMap = new LinkedHashMap<>();
        preferenceMap.put("1-3天", new PreferenceStat("1-3天"));
        preferenceMap.put("4-7天", new PreferenceStat("4-7天"));
        preferenceMap.put("8-14天", new PreferenceStat("8-14天"));
        preferenceMap.put("15-30天", new PreferenceStat("15-30天"));
        preferenceMap.put("30天以上", new PreferenceStat("30天以上"));
        currentOrders.forEach(order -> {
            int rentDays = safeInt(order.getOrderKeep());
            if (rentDays <= 0) {
                return;
            }
            String label = rentalDurationLabel(rentDays);
            PreferenceStat stat = preferenceMap.get(label);
            stat.orderCount++;
            if (order.getUserId() != null) {
                stat.userIds.add(order.getUserId());
            }
        });
        return preferenceMap.values().stream()
                .map(stat -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", stat.label);
                    row.put("userCount", stat.userIds.size());
                    row.put("orderCount", stat.orderCount);
                    return row;
                })
                .collect(Collectors.toList());
    }

    private Map<Integer, UserOrderStat> buildUserOrderStats(List<Order> orders, Map<Integer, Long> paidAmountCents) {
        Map<Integer, UserOrderStat> stats = new HashMap<>();
        orders.forEach(order -> {
            if (order.getUserId() == null) {
                return;
            }
            UserOrderStat stat = stats.computeIfAbsent(order.getUserId(), key -> new UserOrderStat());
            stat.orderCount++;
            stat.totalAmount += amountService.revenueAmountCents(order, paidAmountCents) / 100.0;
            int rentDays = safeInt(order.getOrderKeep());
            if (rentDays > 0 && amountService.isRevenueEligible(order)) {
                stat.totalRentDays += rentDays;
                stat.rentCount++;
            }
            if (order.getCreatetime() != null) {
                stat.lastOrderTime = Math.max(stat.lastOrderTime, order.getCreatetime());
            }
            if (!hasText(stat.userName) && hasText(order.getUserTitle())) {
                stat.userName = order.getUserTitle().trim();
            }
            if (!hasText(stat.province)) {
                stat.province = extractProvince(order.getAddr());
            }
        });
        return stats;
    }

    private Map<String, Long> buildStatusCountMap(List<Order> orders) {
        return orders.stream()
                .collect(Collectors.groupingBy(order -> normalizeAlipayStatus(order.getAlipayStatus()), LinkedHashMap::new, Collectors.counting()));
    }

    private Map<String, Long> buildHourCountMap(List<Order> orders, ZoneId zone) {
        Map<String, Long> result = new TreeMap<>();
        for (int hour = 0; hour < 24; hour++) {
            result.put(String.format(Locale.ROOT, "%02d:00", hour), 0L);
        }
        orders.forEach(order -> {
            if (order.getCreatetime() == null) {
                return;
            }
            int hour = Instant.ofEpochMilli(order.getCreatetime()).atZone(zone).getHour();
            String key = String.format(Locale.ROOT, "%02d:00", hour);
            result.merge(key, 1L, Long::sum);
        });
        return result;
    }

    private Map<String, Long> buildWeekdayCountMap(List<Order> orders, ZoneId zone) {
        Map<String, Long> result = new LinkedHashMap<>();
        List<String> labels = Arrays.asList("周一", "周二", "周三", "周四", "周五", "周六", "周日");
        labels.forEach(label -> result.put(label, 0L));
        orders.forEach(order -> {
            if (order.getCreatetime() == null) {
                return;
            }
            DayOfWeek dayOfWeek = Instant.ofEpochMilli(order.getCreatetime()).atZone(zone).getDayOfWeek();
            String key = labels.get(dayOfWeek.getValue() - 1);
            result.merge(key, 1L, Long::sum);
        });
        return result;
    }

    private Map<String, Long> buildRentalDurationMap(List<Order> orders) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("1-3天", 0L);
        result.put("4-7天", 0L);
        result.put("8-14天", 0L);
        result.put("15-30天", 0L);
        result.put("30天以上", 0L);
        orders.forEach(order -> {
            int rentDays = safeInt(order.getOrderKeep());
            if (rentDays <= 0) {
                return;
            }
            result.merge(rentalDurationLabel(rentDays), 1L, Long::sum);
        });
        return result;
    }

    private String rentalDurationLabel(int rentDays) {
        if (rentDays <= 3) {
            return "1-3天";
        }
        if (rentDays <= 7) {
            return "4-7天";
        }
        if (rentDays <= 14) {
            return "8-14天";
        }
        if (rentDays <= 30) {
            return "15-30天";
        }
        return "30天以上";
    }

    private String resolveUserProvince(User user, Map<Integer, UserOrderStat> userOrderStats) {
        if (user != null) {
            String province = extractProvince(user.getAddr());
            if (hasText(province)) {
                return province;
            }
            if (user.getUserId() != null) {
                UserOrderStat stat = userOrderStats.get(user.getUserId());
                if (stat != null && hasText(stat.province)) {
                    return stat.province;
                }
            }
        }
        return "未知";
    }

    private String resolveProvinceText(User user, String fallback) {
        String province = user == null ? null : extractProvince(user.getAddr());
        if (hasText(province)) {
            return province;
        }
        return hasText(fallback) ? fallback : "未知";
    }

    private String resolveUserName(User user, UserOrderStat stat, Integer userId) {
        if (user != null && hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (user != null && hasText(user.getUserTitle())) {
            return user.getUserTitle().trim();
        }
        if (stat != null && hasText(stat.userName)) {
            return stat.userName;
        }
        return userId == null ? "未知用户" : "用户#" + userId;
    }

    private boolean isAuthenticatedUser(User user) {
        return user != null && (hasText(user.getRealName()) || hasText(user.getIdCard()));
    }

    private boolean isOverdueOrder(Order order) {
        long endTime = order.getOrderEnd() == null ? 0L : order.getOrderEnd();
        return endTime > 0
                && endTime < System.currentTimeMillis()
                && ALIPAY_RENTING.contains(normalizeAlipayStatus(order.getAlipayStatus()));
    }

    private String translateOrderStatus(String status) {
        String normalized = normalizeAlipayStatus(status);
        if ("SIGNED".equals(normalized)) {
            return "待审核";
        }
        if ("APPROVED".equals(normalized)) {
            return "待支付";
        }
        if ("PAID".equals(normalized)) {
            return "已支付";
        }
        if ("DELIVERED".equals(normalized)) {
            return "已发货";
        }
        if ("RECEIVED".equals(normalized)) {
            return "已收货";
        }
        if ("RETURN_DELIVERED".equals(normalized)) {
            return "归还途中";
        }
        if ("RETURN_RECEIVED".equals(normalized)) {
            return "归还收货";
        }
        if ("FINISHED".equals(normalized)) {
            return "订单完结";
        }
        if ("CLOSED".equals(normalized) || "CANCELLED".equals(normalized)) {
            return "已取消";
        }
        if ("REFUNDING".equals(normalized)) {
            return "退款中";
        }
        if ("REFUNDED".equals(normalized)) {
            return "已退款";
        }
        return normalized;
    }

    private Map<String, Object> buildMeta(ZoneId zone, long startMs, long endMs, int orderCount, int userCount) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timezone", zone.getId());
        meta.put("startDate", Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate().format(DATE_FMT));
        meta.put("endDate", Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate().format(DATE_FMT));
        meta.put("orderCount", orderCount);
        meta.put("userCount", userCount);
        meta.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        return meta;
    }

    private ZoneId parseZone(String timezone) {
        if (hasText(timezone)) {
            try {
                return ZoneId.of(timezone.trim());
            } catch (Exception exception) {
                log.warn("无法解析时区 '{}', 使用 Asia/Shanghai", timezone);
            }
        }
        return ZoneId.of("Asia/Shanghai");
    }

    private long[] resolveRange(WebRequest req, ZoneId zone) {
        long[] unifiedPeriodRange = resolveUnifiedPeriodRange(req, zone);
        if (unifiedPeriodRange != null) {
            return unifiedPeriodRange;
        }
        if (req.hasText("startDate") && req.hasText("endDate")) {
            LocalDate start = LocalDate.parse(req.text("startDate"), DATE_FMT);
            LocalDate end = LocalDate.parse(req.text("endDate"), DATE_FMT);
            long startMs = start.atStartOfDay(zone).toInstant().toEpochMilli();
            long endMs = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
            return new long[]{startMs, endMs};
        }

        String rangeType = req.text("rangeType");
        if (!hasText(rangeType)) {
            rangeType = "mtd";
        }
        rangeType = rangeType.toLowerCase(Locale.ROOT);

        LocalDate today = LocalDate.now(zone);
        LocalDate start;
        switch (rangeType) {
            case "today":
                start = today;
                break;
            case "7d":
                start = today.minusDays(6);
                break;
            case "mtd":
            case "month":
            case "month_to_date":
                start = today.withDayOfMonth(1);
                break;
            case "30d":
            case "last_30_days":
                start = today.minusDays(29);
                break;
            case "90d":
            case "last_90_days":
                start = today.minusDays(89);
                break;
            case "180d":
            case "last_180_days":
                start = today.minusDays(179);
                break;
            case "365d":
                start = today.minusDays(364);
                break;
            case "all":
                start = LocalDate.of(2020, 1, 1);
                break;
            default:
                start = today.withDayOfMonth(1);
                break;
        }
        long startMs = start.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
        return new long[]{startMs, endMs};
    }

    private long[] resolveUnifiedPeriodRange(WebRequest req, ZoneId zone) {
        String periodType = normalizePeriodType(req.text("periodType"));
        if (periodType == null) {
            return null;
        }
        LocalDate target = req.hasText("targetDate")
                ? LocalDate.parse(req.text("targetDate"), DATE_FMT)
                : LocalDate.now(zone);
        LocalDate start;
        LocalDate end;
        switch (periodType) {
            case "WEEK":
                start = target.with(DayOfWeek.MONDAY);
                end = start.plusDays(6);
                break;
            case "YEAR":
                start = LocalDate.of(target.getYear(), 1, 1);
                end = LocalDate.of(target.getYear(), 12, 31);
                break;
            case "MONTH":
            default:
                start = target.withDayOfMonth(1);
                end = target.withDayOfMonth(target.lengthOfMonth());
                break;
        }
        long startMs = start.atStartOfDay(zone).toInstant().toEpochMilli();
        long endMs = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
        return new long[]{startMs, endMs};
    }

    private String normalizePeriodType(String periodType) {
        if (!hasText(periodType)) {
            return null;
        }
        String normalized = periodType.trim().toUpperCase(Locale.ROOT);
        return "WEEK".equals(normalized) || "MONTH".equals(normalized) || "YEAR".equals(normalized) ? normalized : null;
    }

    private long[] previousRange(long startMs, long endMs) {
        long span = endMs - startMs;
        return new long[]{startMs - span - 1, startMs - 1};
    }

    private long[] yearOnYearRange(long startMs, long endMs, ZoneId zone) {
        LocalDate start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate().minusYears(1);
        LocalDate end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate().minusYears(1);
        long yearStartMs = start.atStartOfDay(zone).toInstant().toEpochMilli();
        long yearEndMs = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
        return new long[]{yearStartMs, yearEndMs};
    }

    private String resolveGranularity(long startMs, long endMs) {
        long spanDays = (endMs - startMs) / 86_400_000L;
        if (spanDays <= 31) {
            return "day";
        }
        if (spanDays <= 180) {
            return "week";
        }
        return "month";
    }

    private String bucketKey(Long timestamp, ZoneId zone, String granularity) {
        if (timestamp == null) {
            return "unknown";
        }
        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
        if ("week".equals(granularity)) {
            return date.with(DayOfWeek.MONDAY).format(DATE_FMT);
        }
        if ("month".equals(granularity)) {
            return date.withDayOfMonth(1).format(DATE_FMT);
        }
        return date.format(DATE_FMT);
    }

    private void fillMissingBuckets(Map<String, double[]> bucketMap,
                                    long startMs,
                                    long endMs,
                                    ZoneId zone,
                                    String granularity,
                                    int bucketLength) {
        LocalDate start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            String key;
            if ("week".equals(granularity)) {
                key = cursor.with(DayOfWeek.MONDAY).format(DATE_FMT);
                cursor = cursor.plusWeeks(1);
            } else if ("month".equals(granularity)) {
                key = cursor.withDayOfMonth(1).format(DATE_FMT);
                cursor = cursor.plusMonths(1);
            } else {
                key = cursor.format(DATE_FMT);
                cursor = cursor.plusDays(1);
            }
            bucketMap.putIfAbsent(key, new double[bucketLength]);
        }
    }

    private String extractProvince(String address) {
        if (!hasText(address)) {
            return null;
        }
        String text = address.trim();
        for (String[] pair : PROVINCE_PAIRS) {
            if (text.contains(pair[0]) || text.contains(pair[1])) {
                return pair[1];
            }
        }
        return null;
    }

    private double percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return round1(numerator * 100.0 / denominator);
    }

    private double changeRate(double current, double previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return round1((current - previous) * 100.0 / previous);
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static boolean isFinishedStatus(String status) {
        return "FINISHED".equals(status) || "RETURN_RECEIVED".equals(status);
    }

    private static String normalizeAlipayStatus(String alipayStatus) {
        if (!hasText(alipayStatus)) {
            return AlipayRentConstants.STATUS_FINISHED;
        }
        return alipayStatus.trim().toUpperCase(Locale.ROOT);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * 基于金额、频次、租期和活跃度构建用户动态价值画像。
     */
    /**
     * 统一构建用户价值画像。
     * <p>
     * Service 只负责准备样本区间和原始事实，等级、标签、解释文案都交给 helper，
     * 这样后续改口径时可以保持调用方稳定，符合开闭原则。
     */
    private Map<Integer, AnalyticsValueScoreHelper.ValueProfile> buildUserProfiles(Map<Integer, UserOrderStat> userOrderStats, long endMs) {
        Map<Integer, AnalyticsValueScoreHelper.ValueProfile> profiles = new HashMap<>();
        AnalyticsValueScoreHelper.ScoreRange amountRange = new AnalyticsValueScoreHelper.ScoreRange();
        AnalyticsValueScoreHelper.ScoreRange orderRange = new AnalyticsValueScoreHelper.ScoreRange();
        AnalyticsValueScoreHelper.ScoreRange rentDaysRange = new AnalyticsValueScoreHelper.ScoreRange();
        AnalyticsValueScoreHelper.ScoreRange activeDaysRange = new AnalyticsValueScoreHelper.ScoreRange();
        userOrderStats.values().forEach(stat -> {
            amountRange.register(stat.totalAmount);
            orderRange.register(stat.orderCount);
            rentDaysRange.register(stat.rentCount > 0 ? stat.totalRentDays / stat.rentCount : 0D);
            activeDaysRange.register(calcActiveDays(stat.lastOrderTime, endMs));
        });
        userOrderStats.forEach((userId, stat) -> {
            long activeDays = calcActiveDays(stat.lastOrderTime, endMs);
            double amountScore = amountRange.score(stat.totalAmount);
            double orderScore = orderRange.score(stat.orderCount);
            double rentDaysScore = rentDaysRange.score(stat.rentCount > 0 ? stat.totalRentDays / stat.rentCount : 0D);
            double activeScore = activeDaysRange.inverseScore(activeDays);
            profiles.put(userId, AnalyticsValueScoreHelper.buildUserProfile(
                    amountScore, orderScore, activeScore, rentDaysScore, activeDays, stat.orderCount
            ));
        });
        return profiles;
    }

    /**
     * 设备价值上下文。
     */
    /**
     * 先注册样本区间，再统一做设备评分。
     */
    private DeviceValueContext buildDeviceContext(java.util.Collection<DeviceStat> deviceStats) {
        DeviceValueContext context = new DeviceValueContext();
        deviceStats.forEach(stat -> {
            context.amountRange.register(stat.totalAmount);
            context.orderRange.register(stat.orderCount);
            context.rentDaysRange.register(stat.orderCount > 0 ? stat.totalRentDays / (double) stat.orderCount : 0D);
            context.shareRange.register(stat.orderShare);
        });
        return context;
    }

    /**
     * 用统一评分口径回填设备价值结果。
     */
    /**
     * 将设备事实数据映射为统一价值画像输出。
     */
    private void applyDeviceProfile(DeviceStat stat, DeviceValueContext context, int totalOrders) {
        stat.orderShare = totalOrders <= 0 ? 0D : stat.orderCount * 100D / totalOrders;
        double amountScore = context.amountRange.score(stat.totalAmount);
        double orderScore = context.orderRange.score(stat.orderCount);
        double rentDaysScore = context.rentDaysRange.score(stat.orderCount > 0 ? stat.totalRentDays / (double) stat.orderCount : 0D);
        double shareScore = context.shareRange.score(stat.orderShare);
        AnalyticsValueScoreHelper.ValueProfile profile = AnalyticsValueScoreHelper.buildDeviceProfile(
                amountScore, orderScore, rentDaysScore, shareScore
        );
        stat.valueScore = profile.getScore();
        stat.valueLevel = profile.getLevel();
        stat.valueTag = profile.getTag();
        stat.factorSummary = profile.getFactorSummary();
    }

    private double profileScore(AnalyticsValueScoreHelper.ValueProfile profile) {
        return AnalyticsValueScoreHelper.profileScore(profile);
    }

    private long calcActiveDays(long lastOrderTime, long endMs) {
        if (lastOrderTime <= 0L || endMs <= 0L) {
            return 365L;
        }
        return Math.max(0L, (endMs - lastOrderTime) / 86_400_000L);
    }

    private static double numberValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private Map<String, Object> buildSimpleRow(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(firstKey, firstValue);
        row.put(secondKey, secondValue);
        return row;
    }

    private static class UserOrderStat {
        private int orderCount;
        private double totalAmount;
        private double totalRentDays;
        private double rentCount;
        private String userName;
        private String province;
        /**
         * 最近一笔订单时间，用于计算活跃度衰减。
         */
        private long lastOrderTime;
    }

    private static class ProvinceUserStat {
        private String province;
        private long userCount;
        private long authenticatedUsers;
        private long orderUserCount;
        private double totalAmount;
    }

    private static class RegionStat {
        private String name;
        private long orderCount;
        private double totalAmount;
        private double totalRentDays;
        private final Set<Integer> userIds = new HashSet<>();
    }

    private static class DeviceStat {
        private String name;
        private long orderCount;
        private double totalAmount;
        private long totalRentDays;
        /**
         * 订单占比先在当前查询结果内计算，再参与综合评分。
         */
        private double orderShare;
        private double valueScore;
        private String valueLevel;
        private String valueTag;
        private String factorSummary;
    }

    private static class SegmentStat {
        private final String label;
        private long userCount;
        private long orderCount;
        private double totalAmount;
        private double totalScore;

        private SegmentStat(String label) {
            this.label = label;
        }
    }

    private static class PreferenceStat {
        private final String label;
        private long orderCount;
        private final Set<Integer> userIds = new HashSet<>();

        private PreferenceStat(String label) {
            this.label = label;
        }
    }

    /**
     * 设备专题只关心设备维度，不需要保留活跃天数区间，因此单独保留一个轻量上下文。
     */
    private static class DeviceValueContext {
        private final AnalyticsValueScoreHelper.ScoreRange amountRange = new AnalyticsValueScoreHelper.ScoreRange();
        private final AnalyticsValueScoreHelper.ScoreRange orderRange = new AnalyticsValueScoreHelper.ScoreRange();
        private final AnalyticsValueScoreHelper.ScoreRange rentDaysRange = new AnalyticsValueScoreHelper.ScoreRange();
        private final AnalyticsValueScoreHelper.ScoreRange shareRange = new AnalyticsValueScoreHelper.ScoreRange();
    }
}

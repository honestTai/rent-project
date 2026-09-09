package com.fly.rent.web.analytics;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.web.support.WebRequest;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.miniapp.cache.MiniappCacheService;
import com.fly.rent.web.analytics.support.AnalyticsValueScoreHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.fly.rent.config.AlipayRentConstants.STATUS_CLOSED;
import static com.fly.rent.config.AlipayRentConstants.STATUS_PENDING_CANCLE;

/**
 * Web 兼容层仪表盘统计服务。
 * <p>
 * 基于 OrderMapper 做内存聚合，为后台管理前端提供统计仪表盘数据：
 * <ul>
 *   <li>overview  — 当前周期内的汇总指标（订单量、收入、押金、客单价等）</li>
 *   <li>comparison — 与上一同等周期的对比变化率</li>
 *   <li>trend     — 按日/按周/按月的订单量与收入时序数据</li>
 *   <li>rankings  — 设备排行（按订单量与收入）</li>
 *   <li>distributions — 订单状态分布、时段分布</li>
 *   <li>insights  — 自动生成的文字洞察提示</li>
 *   <li>meta      — 查询元数据（时区、起止日期、记录数等）</li>
 * </ul>
 *
 * @author HonestTat
 * @since 2026-03-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebAnalyticsService {

    private final OrderMapper orderMapper;
    private final MiniappCacheService cacheService;
    private final AlipayAnalyticsAmountService amountService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 租赁中对应的支付宝状态（与 MiniappOrderService 一致） */
    private static final Set<String> ALIPAY_RENTING =
            new HashSet<>(Arrays.asList("DELIVERED", "RECEIVED", "RETURN_DELIVERED"));
    /** 取消/关闭对应的支付宝状态 */
    private static final Set<String> ALIPAY_CANCELLED =
            new HashSet<>(Arrays.asList(STATUS_CLOSED, STATUS_PENDING_CANCLE, "CANCELLED", "REFUNDING", "REFUNDED"));

    /** 收货地址省份匹配：长名称优先，用于从 addr 解析省份 */
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

    /**
     * 仪表盘数据入口。
     *
     * @param req 请求参数（timezone、startDate、endDate、rangeType）
     * @return 聚合后的仪表盘 Map
     */
    public Map<String, Object> dashboard(WebRequest req) {
        ZoneId zone = parseZone(req.text("timezone"));
        long[] range = resolveRange(req, zone);
        long startMs = range[0];
        long endMs = range[1];

        if (shouldUseFinishedPeriodCache(endMs)) {
            String cacheKey = buildFinishedPeriodCacheKey("analytics:dashboard:v4", zone, startMs, endMs);
            if (Boolean.TRUE.equals(req.bool("forceRefresh"))) {
                Map<String, Object> result = buildDashboard(req, zone, startMs, endMs);
                cacheService.put(cacheKey, result, 7, TimeUnit.DAYS);
                return result;
            }
            return cacheService.getOrLoad(cacheKey, () -> buildDashboard(req, zone, startMs, endMs), false, 7, TimeUnit.DAYS);
        }

        return buildDashboard(req, zone, startMs, endMs);
    }

    private Map<String, Object> buildDashboard(WebRequest req, ZoneId zone, long startMs, long endMs) {

        long[] prevRange = previousRange(startMs, endMs);
        long prevStartMs = prevRange[0];
        long prevEndMs = prevRange[1];
        long[] yearRange = yearOnYearRange(startMs, endMs, zone);

        List<Order> currentOrders = queryOrders(startMs, endMs);
        List<Order> previousOrders = queryOrders(prevStartMs, prevEndMs);
        List<Order> yearOrders = queryOrders(yearRange[0], yearRange[1]);
        Map<Integer, Long> currentPaidAmountCents = amountService.paidInstallmentAmountCents(currentOrders);
        Map<Integer, Long> previousPaidAmountCents = amountService.paidInstallmentAmountCents(previousOrders);
        Map<Integer, Long> yearPaidAmountCents = amountService.paidInstallmentAmountCents(yearOrders);

        Map<String, Object> distributions = buildDistributions(currentOrders, zone);
        Map<String, Object> rankings = buildRankings(currentOrders, currentPaidAmountCents);
        List<Map<String, Object>> regionRows = buildRegionFromAddresses(currentOrders, currentPaidAmountCents);

        Map<String, Object> overview = buildOverview(currentOrders, currentPaidAmountCents);
        enrichOverview(overview, currentOrders, rankings, distributions, regionRows);

        Map<String, Object> comparison = buildComparison(currentOrders, previousOrders, yearOrders,
                currentPaidAmountCents, previousPaidAmountCents, yearPaidAmountCents);
        enrichComparison(comparison, currentOrders, previousOrders, currentPaidAmountCents, previousPaidAmountCents);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", overview);
        result.put("comparison", comparison);
        result.put("trend", buildTrend(currentOrders, startMs, endMs, zone, currentPaidAmountCents));
        result.put("rankings", rankings);
        result.put("distributions", distributions);
        result.put("geoDistribution", Collections.singletonMap("regions", regionRows));
        result.put("funnel", buildFunnel(currentOrders));
        result.put("activity", buildActivity(currentOrders, zone, currentPaidAmountCents));
        result.put("financialReport", buildFinancialReport(currentOrders, overview, distributions, regionRows, zone, currentPaidAmountCents));
        result.put("insights", buildInsights(currentOrders, previousOrders, currentPaidAmountCents));
        result.put("meta", buildMeta(zone, startMs, endMs, currentOrders.size()));

        return result;
    }

    private boolean shouldUseFinishedPeriodCache(long endMs) {
        return endMs < System.currentTimeMillis();
    }

    private String buildFinishedPeriodCacheKey(String prefix, ZoneId zone, long startMs, long endMs) {
        LocalDate start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate();
        return prefix + ":" + zone.getId() + ":" + start.format(DATE_FMT) + ":" + end.format(DATE_FMT);
    }

    // ========================= 数据查询 =========================

    /**
     * 按时间范围查询订单列表。
     *
     * @param startMs 起始时间戳（含）
     * @param endMs   截止时间戳（含）
     * @return 符合条件的订单列表
     */
    private List<Order> queryOrders(long startMs, long endMs) {
        QueryWrapper<Order> ew = new QueryWrapper<>();
        ew.ge("created_at", startMs);
        ew.le("created_at", endMs);
        return orderMapper.selectList(ew);
    }

    // ========================= Overview =========================

    /**
     * 构建概览数据：总订单量、总收入、总押金、已付款、客单价、活跃租赁数、逾期数、独立用户数。
     *
     * @param orders 当前周期订单列表
     * @return 概览指标 Map
     */
    private Map<String, Object> buildOverview(List<Order> orders, Map<Integer, Long> paidAmountCents) {
        Map<String, Object> ov = new LinkedHashMap<>();

        int totalOrders = orders.size();
        long totalRevenue = amountService.sumRevenueAmountCents(orders, paidAmountCents);
        long totalDeposit = orders.stream()
                .filter(amountService::isRevenueEligible)
                .mapToLong(o -> safeInt(o.getOrderDeposit()))
                .sum();
        long totalPaid = totalRevenue;
        long avgOrderValue = totalOrders > 0 ? totalRevenue / totalOrders : 0;
        long activeRentals = orders.stream()
                .filter(o -> ALIPAY_RENTING.contains(normalizeAlipayStatusForDashboard(o.getAlipayStatus())))
                .count();
        long overdueCount = 0;
        long totalRentDays = orders.stream()
                .filter(amountService::isRevenueEligible)
                .mapToLong(o -> Math.max(safeInt(o.getOrderKeep()), 0))
                .sum();
        Set<Integer> uniqueUsers = orders.stream()
                .map(Order::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        ov.put("totalOrders", totalOrders);
        ov.put("totalRevenueCents", totalRevenue);
        ov.put("totalDepositCents", totalDeposit);
        ov.put("totalPaidCents", totalPaid);
        ov.put("avgOrderValueCents", avgOrderValue);
        ov.put("activeRentals", activeRentals);
        ov.put("activeOrders", activeRentals);
        ov.put("overdueCount", overdueCount);
        ov.put("totalRentDays", totalRentDays);
        ov.put("uniqueUsers", uniqueUsers.size());

        return ov;
    }

    /**
     * 补全 overview 供大屏展示：totalAmount、uniqueDeviceCount、finishedRate、averageRentDays、topDevice、peakHour、peakWeekday、coveredProvinceCount。
     */
    @SuppressWarnings("unchecked")
    private void enrichOverview(Map<String, Object> overview, List<Order> orders,
                               Map<String, Object> rankings, Map<String, Object> distributions,
                               List<Map<String, Object>> regionRows) {
        long totalRevenueCents = ((Number) overview.getOrDefault("totalRevenueCents", 0)).longValue();
        long totalDepositCents = ((Number) overview.getOrDefault("totalDepositCents", 0)).longValue();
        long totalPaidCents = ((Number) overview.getOrDefault("totalPaidCents", 0)).longValue();
        long avgOrderValueCents = ((Number) overview.getOrDefault("avgOrderValueCents", 0)).longValue();
        overview.put("totalAmount", centsToYuan(totalRevenueCents));
        overview.put("totalRevenueYuan", centsToYuan(totalRevenueCents));
        overview.put("totalDepositYuan", centsToYuan(totalDepositCents));
        overview.put("totalPaidYuan", centsToYuan(totalPaidCents));
        overview.put("avgOrderValue", centsToYuan(avgOrderValueCents));
        overview.put("avgOrderValueYuan", centsToYuan(avgOrderValueCents));
        long uniqueDeviceCount = orders.stream()
                .map(o -> hasText(o.getGoodTitle()) ? o.getGoodTitle().trim() : (o.getGoodId() == null ? "" : String.valueOf(o.getGoodId())))
                .filter(this::hasText)
                .distinct()
                .count();
        overview.put("uniqueDeviceCount", uniqueDeviceCount);

        int total = orders.size();
        if (total > 0) {
            long finishedCount = orders.stream()
                    .filter(o -> isFinishedStatus(normalizeAlipayStatusForDashboard(o.getAlipayStatus())))
                    .count();
            long keepSum = orders.stream()
                    .filter(amountService::isRevenueEligible)
                    .mapToLong(o -> safeInt(o.getOrderKeep()))
                    .sum();
            overview.put("finishedRate", String.format("%.1f", finishedCount * 100.0 / total));
            int keepCount = (int) orders.stream()
                    .filter(amountService::isRevenueEligible)
                    .filter(o -> o.getOrderKeep() != null && o.getOrderKeep() > 0)
                    .count();
            overview.put("finishedOrders", finishedCount);
            overview.put("totalRentDays", keepSum);
            overview.put("averageRentDays", keepCount > 0 ? keepSum / keepCount : 0);
        } else {
            overview.put("finishedRate", "0.0");
            overview.put("finishedOrders", 0);
            overview.put("totalRentDays", 0);
            overview.put("averageRentDays", 0);
        }

        List<Map<String, Object>> byOrders = (List<Map<String, Object>>) rankings.get("byOrders");
        if (byOrders != null && !byOrders.isEmpty()) {
            Map<String, Object> first = byOrders.get(0);
            long ordersCount = ((Number) first.getOrDefault("orders", 0)).longValue();
            long revenueCents = ((Number) first.getOrDefault("revenueCents", 0)).longValue();
            String orderShare = total > 0 ? String.format("%.1f", ordersCount * 100.0 / total) : "0.0";
            Map<String, Object> topDevice = new LinkedHashMap<>();
            topDevice.put("name", first.get("name"));
            topDevice.put("orderCount", ordersCount);
            topDevice.put("revenueCents", revenueCents);
            topDevice.put("totalAmount", centsToYuan(revenueCents));
            topDevice.put("totalRevenueYuan", centsToYuan(revenueCents));
            topDevice.put("totalRentDays", first.getOrDefault("totalRentDays", first.getOrDefault("rentalDays", 0)));
            topDevice.put("averageRentDays", first.getOrDefault("averageRentDays", first.getOrDefault("averageRentalDays", 0)));
            topDevice.put("orderShare", orderShare);
            overview.put("topDevice", topDevice);
        } else {
            overview.put("topDevice", null);
        }

        Map<String, Long> byHour = (Map<String, Long>) distributions.get("byHour");
        if (byHour != null && !byHour.isEmpty()) {
            Map.Entry<String, Long> peak = byHour.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            if (peak != null) {
                Map<String, Object> peakHour = new LinkedHashMap<>();
                peakHour.put("label", peak.getKey());
                peakHour.put("count", peak.getValue());
                peakHour.put("rate", total > 0 ? String.format("%.1f", peak.getValue() * 100.0 / total) : "0.0");
                overview.put("peakHour", peakHour);
            }
        }
        Map<String, Long> byWeekday = (Map<String, Long>) distributions.get("byWeekday");
        if (byWeekday != null && !byWeekday.isEmpty()) {
            Map.Entry<String, Long> peak = byWeekday.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null);
            if (peak != null) {
                Map<String, Object> peakWeekday = new LinkedHashMap<>();
                peakWeekday.put("label", peak.getKey());
                overview.put("peakWeekday", peakWeekday);
            }
        }
        overview.put("coveredProvinceCount", regionRows != null ? regionRows.size() : 0);
    }

    private static boolean isFinishedStatus(String alipayStatus) {
        return "FINISHED".equals(alipayStatus) || "RETURN_RECEIVED".equals(alipayStatus);
    }

    // ========================= Comparison =========================

    /**
     * 构建对比数据：输出环比、同比和基准值，字段名与其它分析页保持一致。
     */
    private Map<String, Object> buildComparison(List<Order> current, List<Order> previous, List<Order> year,
                                                Map<Integer, Long> currentPaidAmountCents,
                                                Map<Integer, Long> previousPaidAmountCents,
                                                Map<Integer, Long> yearPaidAmountCents) {
        Map<String, Object> comp = new LinkedHashMap<>();

        int curCount = current.size();
        int prevCount = previous.size();
        int yearCount = year.size();
        long curPaid = amountService.sumRevenueAmountCents(current, currentPaidAmountCents);
        long prevPaid = amountService.sumRevenueAmountCents(previous, previousPaidAmountCents);
        long yearPaid = amountService.sumRevenueAmountCents(year, yearPaidAmountCents);
        long curRevenue = curPaid;
        long prevRevenue = prevPaid;
        long yearRevenue = yearPaid;
        long curUsers = uniqueUserCount(current);
        long prevUsers = uniqueUserCount(previous);
        long yearUsers = uniqueUserCount(year);

        putComparison(comp, "totalOrders", curCount, prevCount, yearCount);
        putComparison(comp, "totalRevenueCents", curRevenue, prevRevenue, yearRevenue);
        putComparison(comp, "totalPaidCents", curPaid, prevPaid, yearPaid);
        putComparison(comp, "uniqueUsers", curUsers, prevUsers, yearUsers);
        putComparison(comp, "activeOrders", activeOrderCount(current), activeOrderCount(previous), activeOrderCount(year));
        putComparison(comp, "totalRentDays", totalRentDays(current), totalRentDays(previous), totalRentDays(year));
        putComparison(comp, "finishedRate", finishedRate(current), finishedRate(previous), finishedRate(year));
        putComparison(comp, "averageRentDays", averageRentDays(current), averageRentDays(previous), averageRentDays(year));

        comp.put("ordersChange", comp.get("totalOrdersMoM"));
        comp.put("revenueChange", comp.get("totalRevenueCentsMoM"));
        comp.put("usersChange", comp.get("uniqueUsersMoM"));
        comp.put("activeOrdersChange", comp.get("activeOrdersMoM"));
        comp.put("paidChange", comp.get("totalPaidCentsMoM"));
        comp.put("previousOrders", prevCount);
        comp.put("previousRevenueCents", prevRevenue);
        comp.put("previousUsers", prevUsers);
        comp.put("samePeriodLastYearOrders", yearCount);
        comp.put("samePeriodLastYearRevenueCents", yearRevenue);
        comp.put("samePeriodLastYearUsers", yearUsers);

        return comp;
    }

    /**
     * 补全 comparison 的文案字段，供前端展示。
     */
    private void enrichComparison(Map<String, Object> comparison, List<Order> current, List<Order> previous,
                                  Map<Integer, Long> currentPaidAmountCents,
                                  Map<Integer, Long> previousPaidAmountCents) {
        int curCount = current.size();
        int prevCount = previous.size();
        double orderChange = changeRate(curCount, prevCount);
        String orderDeltaText = prevCount == 0
                ? (curCount > 0 ? "较上期增加 " + curCount + " 单" : "当前展示服务端统计数据")
                : String.format("较上期%s %.1f%%", orderChange >= 0 ? "增加" : "减少", Math.abs(orderChange));
        comparison.put("orderDeltaText", orderDeltaText);

        long curRev = amountService.sumRevenueAmountCents(current, currentPaidAmountCents);
        long prevRev = amountService.sumRevenueAmountCents(previous, previousPaidAmountCents);
        double amountChange = changeRate(curRev, prevRev);
        String amountDeltaText = prevRev == 0
                ? (curRev > 0 ? "收入较上期有新增" : "")
                : String.format("收入较上期%s %.1f%%", amountChange >= 0 ? "增加" : "减少", Math.abs(amountChange));
        comparison.put("amountDeltaText", amountDeltaText);
    }

    /**
     * 计算环比变化率（百分比），上期为 0 时若本期有值则返回 100%。
     *
     * @param current  当前值
     * @param previous 上期值
     * @return 变化率（如 23.5 表示增长 23.5%）
     */
    private double changeRate(long current, long previous) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round((current - previous) * 10000.0 / previous) / 100.0;
    }

    private void putComparison(Map<String, Object> comparison, String key, double current, double previous, double year) {
        comparison.put(key + "MoM", changeRate(current, previous));
        comparison.put(key + "YoY", changeRate(current, year));
        comparison.put(key + "Previous", previous);
        comparison.put(key + "SamePeriodLastYear", year);
    }

    private double changeRate(double current, double baseline) {
        if (baseline == 0D) {
            return current > 0D ? 100.0 : 0.0;
        }
        return Math.round((current - baseline) * 10000.0 / baseline) / 100.0;
    }

    private long uniqueUserCount(List<Order> orders) {
        return orders.stream().map(Order::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()).size();
    }

    private long activeOrderCount(List<Order> orders) {
        return orders.stream()
                .filter(o -> ALIPAY_RENTING.contains(normalizeAlipayStatusForDashboard(o.getAlipayStatus())))
                .count();
    }

    private long totalRentDays(List<Order> orders) {
        return orders.stream()
                .filter(amountService::isRevenueEligible)
                .mapToLong(o -> Math.max(safeInt(o.getOrderKeep()), 0))
                .sum();
    }

    private double averageRentDays(List<Order> orders) {
        long keepCount = orders.stream()
                .filter(amountService::isRevenueEligible)
                .filter(o -> o.getOrderKeep() != null && o.getOrderKeep() > 0)
                .count();
        return keepCount == 0 ? 0D : Math.round(totalRentDays(orders) * 10.0 / keepCount) / 10.0;
    }

    private double finishedRate(List<Order> orders) {
        if (orders.isEmpty()) {
            return 0D;
        }
        long finished = orders.stream()
                .filter(o -> isFinishedStatus(normalizeAlipayStatusForDashboard(o.getAlipayStatus())))
                .count();
        return Math.round(finished * 1000.0 / orders.size()) / 10.0;
    }

    // ========================= Trend =========================

    /**
     * 构建趋势数据：按日/周/月聚合订单量与收入时序点。
     * 粒度自动选择：≤31 天用 day，≤180 天用 week，否则用 month。
     *
     * @param orders  当前周期订单
     * @param startMs 起始时间戳
     * @param endMs   截止时间戳
     * @param zone    时区
     * @return 趋势数据 Map，包含 granularity 和 points
     */
    private Map<String, Object> buildTrend(List<Order> orders, long startMs, long endMs, ZoneId zone,
                                           Map<Integer, Long> paidAmountCents) {
        Map<String, Object> trend = new LinkedHashMap<>();

        long spanDays = (endMs - startMs) / 86_400_000L;
        String granularity = spanDays <= 31 ? "day" : (spanDays <= 180 ? "week" : "month");

        // [0]=订单数, [1]=收入(分), [2]=租赁天数总和, [3]=有效租期订单数
        Map<String, long[]> buckets = new TreeMap<>();

        for (Order o : orders) {
            String key = bucketKey(o.getCreatetime(), zone, granularity);
            buckets.computeIfAbsent(key, k -> new long[]{0, 0, 0, 0});
            long[] arr = buckets.get(key);
            arr[0]++;
            arr[1] += amountService.revenueAmountCents(o, paidAmountCents);
            int keep = safeInt(o.getOrderKeep());
            if (keep > 0 && amountService.isRevenueEligible(o)) {
                arr[2] += keep;
                arr[3]++;
            }
        }

        fillMissingBuckets(buckets, startMs, endMs, zone, granularity);

        List<Map<String, Object>> points = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : buckets.entrySet()) {
            long[] v = entry.getValue();
            Map<String, Object> point = new LinkedHashMap<>();
            double averageRentDays = v[3] > 0
                    ? Math.round(v[2] * 10.0 / v[3]) / 10.0
                    : 0;
            point.put("date", entry.getKey());
            point.put("orders", v[0]);
            point.put("orderCount", v[0]);
            point.put("revenueCents", v[1]);
            point.put("orderAmount", centsToYuan(v[1]));
            point.put("totalAmount", centsToYuan(v[1]));
            point.put("amount", centsToYuan(v[1]));
            point.put("rentalDays", v[2]);
            point.put("rentDays", v[2]);
            point.put("totalRentDays", v[2]);
            point.put("averageRentalDays", averageRentDays);
            point.put("averageRentDays", averageRentDays);
            point.put("avgRentDays", averageRentDays);
            points.add(point);
        }

        List<String> dates = points.stream()
                .map(p -> String.valueOf(p.get("date")))
                .collect(Collectors.toList());
        List<Long> rentalDaysData = points.stream()
                .map(p -> ((Number) p.get("rentalDays")).longValue())
                .collect(Collectors.toList());
        List<Double> averageRentDaysData = points.stream()
                .map(p -> ((Number) p.get("averageRentDays")).doubleValue())
                .collect(Collectors.toList());

        trend.put("granularity", granularity);
        trend.put("points", points);
        trend.put("dates", dates);
        trend.put("labels", dates);
        trend.put("categories", dates);
        trend.put("xAxis", dates);
        trend.put("rentalDaysData", rentalDaysData);
        trend.put("rentalDaysSeries", rentalDaysData);
        trend.put("rentDaysData", rentalDaysData);
        trend.put("rentDaysSeries", rentalDaysData);
        trend.put("averageRentDaysData", averageRentDaysData);
        trend.put("averageRentDaysSeries", averageRentDaysData);
        trend.put("averageRentalDaysData", averageRentDaysData);
        trend.put("averageRentalDaysSeries", averageRentDaysData);
        trend.put("avgRentDaysData", averageRentDaysData);
        trend.put("avgRentDaysSeries", averageRentDaysData);

        return trend;
    }

    /**
     * 根据粒度将时间戳映射为聚合桶的 key。
     *
     * @param timestamp   订单创建时间戳
     * @param zone        时区
     * @param granularity 粒度：day / week / month
     * @return 聚合桶 key（yyyy-MM-dd 格式）
     */
    private String bucketKey(Long timestamp, ZoneId zone, String granularity) {
        if (timestamp == null) return "unknown";
        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate();
        switch (granularity) {
            case "week":
                LocalDate monday = date.with(java.time.DayOfWeek.MONDAY);
                return monday.format(DATE_FMT);
            case "month":
                return date.withDayOfMonth(1).format(DATE_FMT);
            default:
                return date.format(DATE_FMT);
        }
    }

    /**
     * 补全时间范围内缺失的聚合桶（无数据的时间段填充 [0, 0]），确保趋势图连续。
     *
     * @param buckets     已有聚合桶
     * @param startMs     起始时间戳
     * @param endMs       截止时间戳
     * @param zone        时区
     * @param granularity 粒度：day / week / month
     */
    private void fillMissingBuckets(Map<String, long[]> buckets, long startMs, long endMs,
                                    ZoneId zone, String granularity) {
        LocalDate start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate();
        LocalDate cursor = start;

        while (!cursor.isAfter(end)) {
            String key;
            switch (granularity) {
                case "week":
                    key = cursor.with(java.time.DayOfWeek.MONDAY).format(DATE_FMT);
                    cursor = cursor.plusWeeks(1);
                    break;
                case "month":
                    key = cursor.withDayOfMonth(1).format(DATE_FMT);
                    cursor = cursor.plusMonths(1);
                    break;
                default:
                    key = cursor.format(DATE_FMT);
                    cursor = cursor.plusDays(1);
                    break;
            }
            buckets.putIfAbsent(key, new long[]{0, 0, 0, 0});
        }
    }

    // ========================= Rankings =========================

    /**
     * 构建设备排行榜：按订单量排序的 Top10 和按收入排序的 Top10。
     *
     * @param orders 当前周期订单
     * @return 排行榜 Map，包含 byOrders 和 byRevenue
     */
    private Map<String, Object> buildRankings(List<Order> orders, Map<Integer, Long> paidAmountCents) {
        Map<String, Object> rankings = new LinkedHashMap<>();

        // [0]=订单数, [1]=收入(分), [2]=租赁天数总和, [3]=有效租期订单数(orderKeep>0)
        Map<String, long[]> deviceMap = new LinkedHashMap<>();
        for (Order o : orders) {
            String goodTitle = o.getGoodTitle();
            if (goodTitle == null || goodTitle.trim().isEmpty()) {
                goodTitle = "未知设备";
            }
            deviceMap.computeIfAbsent(goodTitle, k -> new long[]{0, 0, 0, 0});
            long[] arr = deviceMap.get(goodTitle);
            arr[0]++;
            arr[1] += amountService.revenueAmountCents(o, paidAmountCents);
            int keep = safeInt(o.getOrderKeep());
            if (keep > 0 && amountService.isRevenueEligible(o)) {
                arr[2] += keep;
                arr[3]++;
            }
        }

        int totalOrders = orders.size();
        Map<String, AnalyticsValueScoreHelper.ValueProfile> profileMap = buildDeviceProfiles(deviceMap, totalOrders);

        List<Map<String, Object>> byOrders = deviceMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(10)
                .map(e -> buildRankingItem(e, totalOrders, profileMap.get(e.getKey())))
                .collect(Collectors.toList());

        List<Map<String, Object>> byRevenue = deviceMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .limit(10)
                .map(e -> buildRankingItem(e, totalOrders, profileMap.get(e.getKey())))
                .collect(Collectors.toList());

        rankings.put("byOrders", byOrders);
        rankings.put("byRevenue", byRevenue);

        return rankings;
    }

    private Map<String, Object> buildRankingItem(Map.Entry<String, long[]> e,
                                                 int totalOrders,
                                                 AnalyticsValueScoreHelper.ValueProfile profile) {
        long[] v = e.getValue();
        Map<String, Object> item = new LinkedHashMap<>();
        double averageRentDays = v[3] > 0
                ? Math.round(v[2] * 10.0 / v[3]) / 10.0
                : 0;
        item.put("name", e.getKey());
        item.put("orders", v[0]);
        item.put("orderCount", v[0]);
        item.put("revenueCents", v[1]);
        item.put("totalAmount", centsToYuan(v[1]));
        item.put("totalRevenueYuan", centsToYuan(v[1]));
        item.put("rentalDays", v[2]);
        item.put("rentDays", v[2]);
        item.put("totalRentDays", v[2]);
        item.put("averageRentalDays", averageRentDays);
        item.put("averageRentDays", averageRentDays);
        item.put("avgRentDays", averageRentDays);
        item.put("orderShare", totalOrders > 0
                ? String.format("%.1f", v[0] * 100.0 / totalOrders)
                : "0.0");
        item.put("valueScore", profile == null ? 0D : profile.getScore());
        item.put("valueLevel", profile == null ? "D" : profile.getLevel());
        item.put("valueTag", profile == null ? "稳健设备" : profile.getTag());
        item.put("factorSummary", profile == null ? "暂无模型结果" : profile.getFactorSummary());
        return item;
    }

    /**
     * 为设备排行补充动态价值分，综合收入、订单频次、租期和覆盖占比。
     */
    /**
     * 首页看板只关心结果，不直接感知评分细节。
     * 这里集中完成区间注册和画像构建，后续新增利润、故障率等维度时可以在此扩展。
     */
    private Map<String, AnalyticsValueScoreHelper.ValueProfile> buildDeviceProfiles(Map<String, long[]> deviceMap, int totalOrders) {
        AnalyticsValueScoreHelper.ScoreRange revenueRange = new AnalyticsValueScoreHelper.ScoreRange();
        AnalyticsValueScoreHelper.ScoreRange orderRange = new AnalyticsValueScoreHelper.ScoreRange();
        AnalyticsValueScoreHelper.ScoreRange rentDaysRange = new AnalyticsValueScoreHelper.ScoreRange();
        AnalyticsValueScoreHelper.ScoreRange shareRange = new AnalyticsValueScoreHelper.ScoreRange();
        deviceMap.values().forEach(value -> {
            revenueRange.register(value[1]);
            orderRange.register(value[0]);
            rentDaysRange.register(value[3] > 0 ? value[2] * 1.0 / value[3] : 0D);
            shareRange.register(totalOrders > 0 ? value[0] * 100D / totalOrders : 0D);
        });
        Map<String, AnalyticsValueScoreHelper.ValueProfile> result = new LinkedHashMap<>();
        deviceMap.forEach((name, value) -> {
            double revenueScore = revenueRange.score(value[1]);
            double orderScore = orderRange.score(value[0]);
            double rentDaysScore = rentDaysRange.score(value[3] > 0 ? value[2] * 1.0 / value[3] : 0D);
            double shareScore = shareRange.score(totalOrders > 0 ? value[0] * 100D / totalOrders : 0D);
            result.put(name, AnalyticsValueScoreHelper.buildDashboardDeviceProfile(
                    revenueScore, orderScore, rentDaysScore, shareScore
            ));
        });
        return result;
    }

    // ========================= Distributions =========================

    /**
     * 构建分布数据：按支付宝状态、小时、星期、租赁时长维度统计。
     * 大屏仅使用支付宝状态；alipayStatus 为 null 或空时视为已完成（FINISHED）。
     *
     * @param orders 当前周期订单
     * @param zone   时区
     * @return 分布 Map，包含 byAlipayStatus / byHour / byWeekday / byRentalDuration
     */
    private Map<String, Object> buildDistributions(List<Order> orders, ZoneId zone) {
        Map<String, Object> dist = new LinkedHashMap<>();

        Map<String, Long> alipayStatusDist = orders.stream()
                .collect(Collectors.groupingBy(
                        o -> normalizeAlipayStatusForDashboard(o.getAlipayStatus()),
                        Collectors.counting()
                ));
        dist.put("byAlipayStatus", alipayStatusDist);
        dist.put("status", countMapToRows(alipayStatusDist, true));
        dist.put("orderStatus", countMapToRows(alipayStatusDist, true));

        Map<String, Long> hourDist = new TreeMap<>();
        for (int h = 0; h < 24; h++) {
            hourDist.put(String.format("%02d:00", h), 0L);
        }
        for (Order o : orders) {
            if (o.getCreatetime() != null) {
                int hour = Instant.ofEpochMilli(o.getCreatetime())
                        .atZone(zone).getHour();
                String key = String.format("%02d:00", hour);
                hourDist.merge(key, 1L, (a, b) -> a + b);
            }
        }
        dist.put("byHour", hourDist);
        dist.put("hourly", countMapToRows(hourDist, false));
        dist.put("hours", countMapToRows(hourDist, false));

        Map<String, Long> weekdayDist = new LinkedHashMap<>();
        String[] dayNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (String d : dayNames) weekdayDist.put(d, 0L);
        for (Order o : orders) {
            if (o.getCreatetime() != null) {
                DayOfWeek dow = Instant.ofEpochMilli(o.getCreatetime())
                        .atZone(zone).getDayOfWeek();
                String name = dayNames[dow.getValue() - 1];
                weekdayDist.merge(name, 1L, (a, b) -> a + b);
            }
        }
        dist.put("byWeekday", weekdayDist);
        dist.put("weekday", countMapToRows(weekdayDist, false));
        dist.put("weekdays", countMapToRows(weekdayDist, false));

        Map<String, Long> rentalDurationDist = new LinkedHashMap<>();
        rentalDurationDist.put("1-3天", 0L);
        rentalDurationDist.put("4-7天", 0L);
        rentalDurationDist.put("8-14天", 0L);
        rentalDurationDist.put("15-30天", 0L);
        rentalDurationDist.put("30天以上", 0L);
        for (Order o : orders) {
            int keep = safeInt(o.getOrderKeep());
            if (keep <= 0) continue;
            String bucket;
            if (keep <= 3) bucket = "1-3天";
            else if (keep <= 7) bucket = "4-7天";
            else if (keep <= 14) bucket = "8-14天";
            else if (keep <= 30) bucket = "15-30天";
            else bucket = "30天以上";
            rentalDurationDist.merge(bucket, 1L, (a, b) -> a + b);
        }
        dist.put("byRentalDuration", rentalDurationDist);
        dist.put("rentalDuration", countMapToRows(rentalDurationDist, false));

        return dist;
    }

    private List<Map<String, Object>> countMapToRows(Map<String, Long> source, boolean translateStatus) {
        return source.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("label", translateStatus ? translateOrderStatus(entry.getKey()) : entry.getKey());
                    row.put("status", entry.getKey());
                    row.put("count", entry.getValue());
                    row.put("orderCount", entry.getValue());
                    row.put("value", entry.getValue());
                    return row;
                })
                .sorted((left, right) -> Long.compare(
                        ((Number) right.get("count")).longValue(),
                        ((Number) left.get("count")).longValue()
                ))
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildFunnel(List<Order> orders) {
        long signed = countByStatus(orders, "SIGNED");
        long paid = countByStatus(orders, "PAID") + countByStatus(orders, "APPROVED");
        long delivered = countByStatus(orders, "DELIVERED") + countByStatus(orders, "RECEIVED") + countByStatus(orders, "RETURN_DELIVERED");
        long finished = countByStatus(orders, "FINISHED") + countByStatus(orders, "RETURN_RECEIVED");

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(buildFunnelRow("下单签约", signed + paid + delivered + finished, orders.size()));
        rows.add(buildFunnelRow("租金支付", paid + delivered + finished, orders.size()));
        rows.add(buildFunnelRow("设备履约", delivered + finished, orders.size()));
        rows.add(buildFunnelRow("归还完结", finished, orders.size()));

        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("rows", rows);
        funnel.put("completionRate", orders.isEmpty() ? "0.0" : String.format("%.1f", finished * 100.0 / orders.size()));
        return funnel;
    }

    private long countByStatus(List<Order> orders, String status) {
        return orders.stream()
                .filter(order -> status.equals(normalizeAlipayStatusForDashboard(order.getAlipayStatus())))
                .count();
    }

    private Map<String, Object> buildFunnelRow(String label, long count, int total) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("count", count);
        row.put("rate", total > 0 ? String.format("%.1f", count * 100.0 / total) : "0.0");
        return row;
    }

    private Map<String, Object> buildActivity(List<Order> orders, ZoneId zone, Map<Integer, Long> paidAmountCents) {
        List<Map<String, Object>> recentOrders = orders.stream()
                .sorted((left, right) -> Long.compare(
                        right.getCreatetime() == null ? 0L : right.getCreatetime(),
                        left.getCreatetime() == null ? 0L : left.getCreatetime()
                ))
                .limit(8)
                .map(order -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("orderId", order.getOrderId());
                    row.put("orderNo", hasText(order.getOrderNo()) ? order.getOrderNo() : order.getRentOrderId());
                    row.put("userName", hasText(order.getUserTitle()) ? order.getUserTitle() : "未知用户");
                    row.put("deviceName", hasText(order.getGoodTitle()) ? order.getGoodTitle() : "未知设备");
                    row.put("status", normalizeAlipayStatusForDashboard(order.getAlipayStatus()));
                    row.put("statusLabel", translateOrderStatus(order.getAlipayStatus()));
                    row.put("amount", amountService.revenueAmountCents(order, paidAmountCents) / 100.0);
                    row.put("rentDays", Math.max(safeInt(order.getOrderKeep()), 0));
                    row.put("createdAt", formatDateTime(order.getCreatetime(), zone));
                    return row;
                })
                .collect(Collectors.toList());

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("recentOrders", recentOrders);
        activity.put("latestOrderAt", recentOrders.isEmpty() ? "" : recentOrders.get(0).get("createdAt"));
        return activity;
    }

    // ========================= 地图：收货地址省份聚合 =========================

    /**
     * 从订单收货地址（addr）聚合省份维度：省份名、订单数、金额（分）。
     * 用于大屏地图与省份热榜。
     */
    private List<Map<String, Object>> buildRegionFromAddresses(List<Order> orders, Map<Integer, Long> paidAmountCents) {
        Map<String, long[]> regionMap = new LinkedHashMap<>();
        for (Order o : orders) {
            String addr = o.getAddr();
            String province = extractProvinceFromAddr(addr);
            if (province == null || province.isEmpty()) {
                continue;
            }
            regionMap.computeIfAbsent(province, k -> new long[]{0, 0});
            long[] arr = regionMap.get(province);
            arr[0]++;
            arr[1] += amountService.revenueAmountCents(o, paidAmountCents);
        }
        int total = orders.size();
        return regionMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", e.getKey());
                    row.put("orderCount", e.getValue()[0]);
                    row.put("totalAmount", e.getValue()[1] / 100.0);
                    row.put("orderShare", total > 0 ? String.format("%.1f", e.getValue()[0] * 100.0 / total) : "0.0");
                    return row;
                })
                .collect(Collectors.toList());
    }

    /**
     * 财务报表聚合块与首页时间范围完全一致，方便首页弹窗直接展示和导出。
     */
    private Map<String, Object> buildFinancialReport(List<Order> orders,
                                                     Map<String, Object> overview,
                                                     Map<String, Object> distributions,
                                                     List<Map<String, Object>> regionRows,
                                                     ZoneId zone,
                                                     Map<Integer, Long> paidAmountCents) {
        Map<String, Object> report = new LinkedHashMap<>();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRevenueCents", overview.getOrDefault("totalRevenueCents", 0));
        summary.put("totalPaidCents", overview.getOrDefault("totalPaidCents", 0));
        summary.put("totalDepositCents", overview.getOrDefault("totalDepositCents", 0));
        summary.put("avgOrderValueCents", overview.getOrDefault("avgOrderValueCents", 0));
        summary.put("totalRevenueYuan", overview.getOrDefault("totalRevenueYuan", 0));
        summary.put("totalPaidYuan", overview.getOrDefault("totalPaidYuan", 0));
        summary.put("totalDepositYuan", overview.getOrDefault("totalDepositYuan", 0));
        summary.put("avgOrderValueYuan", overview.getOrDefault("avgOrderValueYuan", 0));
        summary.put("activeRentals", overview.getOrDefault("activeRentals", 0));
        summary.put("totalRentDays", overview.getOrDefault("totalRentDays", 0));
        report.put("summary", summary);
        report.put("trendRows", buildFinancialTrendRows(orders, zone, paidAmountCents));
        report.put("statusRows", buildFinancialStatusRows(distributions));
        report.put("deviceRows", buildFinancialDeviceRows(orders, paidAmountCents));
        report.put("regionRows", buildFinancialRegionRows(regionRows));
        return report;
    }

    private List<Map<String, Object>> buildFinancialTrendRows(List<Order> orders,
                                                              ZoneId zone,
                                                              Map<Integer, Long> paidAmountCents) {
        Map<String, long[]> trendMap = new TreeMap<>();
        for (Order order : orders) {
            if (order.getCreatetime() == null) {
                continue;
            }
            String date = Instant.ofEpochMilli(order.getCreatetime()).atZone(zone).toLocalDate().format(DATE_FMT);
            long[] stat = trendMap.computeIfAbsent(date, key -> new long[]{0, 0, 0, 0});
            long paidAmount = amountService.revenueAmountCents(order, paidAmountCents);
            stat[0] += paidAmount;
            stat[1] += 1;
            if (amountService.isRevenueEligible(order)) {
                stat[2] += Math.max(safeInt(order.getOrderKeep()), 0);
            }
            stat[3] += paidAmount;
        }
        long cumulative = 0L;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : trendMap.entrySet()) {
            cumulative += entry.getValue()[0];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey());
            row.put("amountCents", entry.getValue()[0]);
            row.put("amountYuan", centsToYuan(entry.getValue()[0]));
            row.put("cumulativeAmountCents", cumulative);
            row.put("cumulativeAmountYuan", centsToYuan(cumulative));
            row.put("orderCount", entry.getValue()[1]);
            row.put("rentDays", entry.getValue()[2]);
            row.put("paidAmountCents", entry.getValue()[3]);
            row.put("paidAmountYuan", centsToYuan(entry.getValue()[3]));
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildFinancialStatusRows(Map<String, Object> distributions) {
        Map<String, Long> byStatus = (Map<String, Long>) distributions.getOrDefault("byAlipayStatus", Collections.emptyMap());
        List<Map<String, Object>> rows = new ArrayList<>();
        byStatus.forEach((status, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", status);
            row.put("orderCount", count);
            rows.add(row);
        });
        rows.sort((left, right) -> Long.compare(((Number) right.get("orderCount")).longValue(), ((Number) left.get("orderCount")).longValue()));
        return rows;
    }

    private List<Map<String, Object>> buildFinancialDeviceRows(List<Order> orders, Map<Integer, Long> paidAmountCents) {
        Map<String, long[]> deviceMap = new LinkedHashMap<>();
        for (Order order : orders) {
            String name = order.getGoodTitle();
            if (name == null || name.trim().isEmpty()) {
                name = "未知设备";
            }
            long[] stat = deviceMap.computeIfAbsent(name, key -> new long[]{0, 0, 0});
            stat[0] += 1;
            stat[1] += amountService.revenueAmountCents(order, paidAmountCents);
            if (amountService.isRevenueEligible(order)) {
                stat[2] += Math.max(safeInt(order.getOrderKeep()), 0);
            }
        }
        return deviceMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]))
                .limit(10)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", entry.getKey());
                    row.put("orderCount", entry.getValue()[0]);
                    row.put("amountCents", entry.getValue()[1]);
                    row.put("amountYuan", centsToYuan(entry.getValue()[1]));
                    row.put("totalAmount", centsToYuan(entry.getValue()[1]));
                    row.put("rentDays", entry.getValue()[2]);
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> buildFinancialRegionRows(List<Map<String, Object>> regionRows) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : regionRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", item.getOrDefault("name", "未知地区"));
            row.put("orderCount", item.getOrDefault("orderCount", 0));
            row.put("amount", item.getOrDefault("totalAmount", 0));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 从收货地址字符串解析省份简称。优先匹配 PROVINCE_PAIRS 中的长名称。
     */
    private static String extractProvinceFromAddr(String addr) {
        if (addr == null || addr.trim().isEmpty()) {
            return null;
        }
        String s = addr.trim();
        for (String[] pair : PROVINCE_PAIRS) {
            if (s.contains(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    // ========================= Insights =========================

    /**
     * 自动生成业务洞察文字提示：订单增长/下降、取消率（按支付宝状态）、客单价、最热设备。
     *
     * @param current  当前周期订单
     * @param previous 上一周期订单
     * @return 洞察文案列表
     */
    private List<String> buildInsights(List<Order> current, List<Order> previous, Map<Integer, Long> currentPaidAmountCents) {
        List<String> insights = new ArrayList<>();

        int curCount = current.size();
        int prevCount = previous.size();

        if (prevCount > 0) {
            double orderChange = changeRate(curCount, prevCount);
            if (orderChange > 20) {
                insights.add(String.format("订单量较上一周期增长 %.1f%%，业务增长良好。", orderChange));
            } else if (orderChange < -20) {
                insights.add(String.format("订单量较上一周期下降 %.1f%%，建议关注。", Math.abs(orderChange)));
            }
        }

        long cancelledCount = current.stream()
                .filter(o -> ALIPAY_CANCELLED.contains(normalizeAlipayStatusForDashboard(o.getAlipayStatus())))
                .count();
        if (curCount > 0 && cancelledCount > 0) {
            double cancelRate = Math.round(cancelledCount * 10000.0 / curCount) / 100.0;
            if (cancelRate > 15) {
                insights.add(String.format("取消率 %.1f%%（%d 单），可能存在商品或流程问题。", cancelRate, cancelledCount));
            }
        }

        if (curCount > 0) {
            long avgRevenue = amountService.sumRevenueAmountCents(current, currentPaidAmountCents) / curCount;
            insights.add(String.format("周期内客单价平均 %.2f 元。", avgRevenue / 100.0));
        }

        Optional<Map.Entry<String, Long>> topDevice = current.stream()
                .collect(Collectors.groupingBy(
                        o -> o.getGoodTitle() == null ? "未知" : o.getGoodTitle(),
                        Collectors.counting()))
                .entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue));
        topDevice.ifPresent(e ->
                insights.add(String.format("最热门设备「%s」共 %d 单。", e.getKey(), e.getValue()))
        );

        if (insights.isEmpty()) {
            insights.add("当前周期数据正常，暂无特别洞察。");
        }

        return insights;
    }

    // ========================= Meta =========================

    /**
     * 构建查询元数据：时区、起止日期、记录数、生成时间。
     *
     * @param zone        时区
     * @param startMs     起始时间戳
     * @param endMs       截止时间戳
     * @param recordCount 本次查询记录数
     * @return 元数据 Map
     */
    private Map<String, Object> buildMeta(ZoneId zone, long startMs, long endMs, int recordCount) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("timezone", zone.getId());
        String startDate = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate().format(DATE_FMT);
        String endDate = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate().format(DATE_FMT);
        meta.put("startDate", startDate);
        meta.put("endDate", endDate);
        meta.put("recordCount", recordCount);
        meta.put("totalRows", recordCount);
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("startDate", startDate);
        range.put("endDate", endDate);
        meta.put("dataRange", range);
        meta.put("queryRange", new LinkedHashMap<>(range));
        meta.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        return meta;
    }

    // ========================= 时间范围解析 =========================

    /**
     * 解析时区字符串，无效或为空时回退到 Asia/Shanghai。
     *
     * @param timezone 时区标识（如 "Asia/Shanghai"、"UTC+8"）
     * @return 时区
     */
    private ZoneId parseZone(String timezone) {
        if (timezone != null && !timezone.trim().isEmpty()) {
            try {
                return ZoneId.of(timezone.trim());
            } catch (Exception e) {
                log.warn("无法解析时区 '{}', 使用 Asia/Shanghai", timezone);
            }
        }
        return ZoneId.of("Asia/Shanghai");
    }

    /**
     * 解析请求中的日期范围。
     * <ul>
     *   <li>优先使用 startDate / endDate（yyyy-MM-dd 格式）</li>
     *   <li>其次根据 rangeType 推算：today / 7d / mtd（本月至今）/ month / 30d / last_30_days / 90d / 180d / 365d / all</li>
     *   <li>未指定 rangeType 时默认 mtd（本月至今）</li>
     * </ul>
     *
     * @return long[2] = { startMs, endMs }
     */
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
        if (rangeType == null) {
            rangeType = "mtd";
        }
        rangeType = rangeType.toLowerCase();

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
                start = today.minusDays(29);
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
        if (periodType == null || periodType.trim().isEmpty()) {
            return null;
        }
        String normalized = periodType.trim().toUpperCase(Locale.ROOT);
        return "WEEK".equals(normalized) || "MONTH".equals(normalized) || "YEAR".equals(normalized) ? normalized : null;
    }

    /**
     * 计算上一同等周期的时间范围（用于环比对比）。
     *
     * @param startMs 当前周期起始
     * @param endMs   当前周期截止
     * @return long[2] = { 上期起始, 上期截止 }
     */
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

    // ========================= 辅助方法 =========================

    /** null 安全的 Integer 转 int，null 时返回 0。 */
    private static int safeInt(Integer val) {
        return val == null ? 0 : val;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String formatDateTime(Long timestamp, ZoneId zone) {
        if (timestamp == null || timestamp <= 0) {
            return "";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(zone).format(Instant.ofEpochMilli(timestamp));
    }

    private String translateOrderStatus(String status) {
        String normalized = normalizeAlipayStatusForDashboard(status);
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

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double centsToYuan(long cents) {
        return round2(cents / 100.0);
    }

    /**
     * 大屏用：支付宝状态为空时视为已完成。
     * alipayStatus 为 null 或空字符串时返回 {@link AlipayRentConstants#STATUS_FINISHED}。
     */
    private static String normalizeAlipayStatusForDashboard(String alipayStatus) {
        if (alipayStatus == null || alipayStatus.trim().isEmpty()) {
            return AlipayRentConstants.STATUS_FINISHED;
        }
        return alipayStatus;
    }

}

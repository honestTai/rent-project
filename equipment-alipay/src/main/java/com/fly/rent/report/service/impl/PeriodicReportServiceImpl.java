package com.fly.rent.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.common.notify.feishu.FeishuNotifyProperties;
import com.common.notify.feishu.FeishuNotifyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fly.rent.report.dto.PeriodicReportGenerateDto;
import com.fly.rent.report.dto.PeriodicReportQueryDto;
import com.fly.rent.report.entity.PeriodicReport;
import com.fly.rent.report.mapper.PeriodicReportMapper;
import com.fly.rent.report.service.PeriodicReportService;
import com.fly.rent.report.vo.PeriodicReportDetailVo;
import com.fly.rent.report.vo.PeriodicReportListVo;
import com.fly.rent.web.analytics.WebAnalyticsService;
import com.fly.rent.web.analytics.WebAnalyticsTopicsService;
import com.fly.rent.web.support.WebRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付宝租赁周期报表服务实现。
 */
@Slf4j
@Service
public class PeriodicReportServiceImpl implements PeriodicReportService {
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter YEAR_FMT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private PeriodicReportMapper periodicReportMapper;
    @Autowired
    private WebAnalyticsService webAnalyticsService;
    @Autowired
    private WebAnalyticsTopicsService webAnalyticsTopicsService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FeishuNotifyService feishuNotifyService;
    @Autowired
    private FeishuNotifyProperties feishuNotifyProperties;

    /**
     * 查询历史报表列表，支持前端按类型、生成方式与周期范围筛选。
     */
    @Override
    public List<PeriodicReportListVo> listReports(PeriodicReportQueryDto queryDto) {
        LambdaQueryWrapper<PeriodicReport> wrapper = new LambdaQueryWrapper<>();
        if (queryDto != null) {
            if (hasText(queryDto.getReportType())) {
                wrapper.eq(PeriodicReport::getReportType, queryDto.getReportType());
            }
            if (hasText(queryDto.getGenerateMode())) {
                wrapper.eq(PeriodicReport::getGenerateMode, queryDto.getGenerateMode());
            }
            if (queryDto.getStartTime() != null) {
                wrapper.ge(PeriodicReport::getPeriodStartTime, queryDto.getStartTime());
            }
            if (queryDto.getEndTime() != null) {
                wrapper.le(PeriodicReport::getPeriodEndTime, queryDto.getEndTime());
            }
        }
        wrapper.orderByDesc(PeriodicReport::getPeriodEndTime, PeriodicReport::getId);
        List<PeriodicReportListVo> result = new ArrayList<>();
        for (PeriodicReport report : periodicReportMapper.selectList(wrapper)) {
            result.add(toListVo(report));
        }
        return result;
    }

    /**
     * 查询单条报表详情，并把落库快照转换为页面可直接渲染的数据结构。
     */
    @Override
    public PeriodicReportDetailVo getReportDetail(Integer id) {
        PeriodicReport report = periodicReportMapper.selectById(id);
        if (report == null) {
            return null;
        }
        PeriodicReportListVo summary = toListVo(report);
        Map<String, Object> content = readContent(report.getContentJson());
        PeriodicReportDetailVo detailVo = new PeriodicReportDetailVo();
        detailVo.setSummary(summary);
        List<Map<String, Object>> summaryCards = extractRows(content.get("summaryCards"));
        detailVo.setSummaryCards(summaryCards.isEmpty() ? buildSummaryCards(summary) : summaryCards);
        List<Map<String, Object>> trendRows = normalizeTrendRows(extractRows(content.get("trendRows")));
        detailVo.setTrendRows(trendRows.isEmpty() ? buildTrendRows(report, summary) : trendRows);
        List<Map<String, Object>> keySections = extractRows(content.get("keySections"));
        detailVo.setKeySections(keySections.isEmpty() ? buildKeySections(summary) : keySections);
        return detailVo;
    }

    /**
     * 手动补生成历史周期报表；若同周期已存在，则按当前统计口径刷新快照。
     */
    @Override
    public PeriodicReport generateReport(PeriodicReportGenerateDto generateDto) {
        if (generateDto == null || generateDto.getTargetDate() == null || !hasText(generateDto.getReportType())) {
            throw new IllegalArgumentException("报表类型和目标时间不能为空");
        }
        ReportPeriod period = resolvePeriod(generateDto.getReportType(), generateDto.getTargetDate());
        assertFinishedPeriod(period);
        return getOrCreateReport(period, "MANUAL");
    }

    /**
     * 定时生成上一自然周报表。
     */
    @Override
    public void generatePreviousWeekReport() {
        getOrCreateReport(previousWeekPeriod(), "AUTO");
    }

    /**
     * 定时生成上一自然月报表。
     */
    @Override
    public void generatePreviousMonthReport() {
        getOrCreateReport(previousMonthPeriod(), "AUTO");
    }

    /**
     * 定时生成上一自然年报表。
     */
    @Override
    public void generatePreviousYearReport() {
        getOrCreateReport(previousYearPeriod(), "AUTO");
    }

    /**
     * 复用 dashboard 与 subjects 两套分析聚合，生成并持久化一份周期快照。
     */
    private PeriodicReport getOrCreateReport(ReportPeriod period, String generateMode) {
        LambdaQueryWrapper<PeriodicReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PeriodicReport::getReportType, period.getReportType())
                .eq(PeriodicReport::getPeriodStartTime, period.getStartTime())
                .eq(PeriodicReport::getPeriodEndTime, period.getEndTime())
                .last("limit 1");
        PeriodicReport existing = periodicReportMapper.selectOne(wrapper);
        boolean manualRefresh = existing != null && "MANUAL".equalsIgnoreCase(generateMode);
        if (existing != null && !manualRefresh) {
            return existing;
        }
        try {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("rangeType", "CUSTOM");
            query.put("timezone", "Asia/Shanghai");
            query.put("startDate", period.getStartDate());
            query.put("endDate", period.getEndDate());
            query.put("topN", 20);
            Map<String, Object> dashboard = webAnalyticsService.dashboard(WebRequest.of(query));
            Map<String, Object> subjects = webAnalyticsTopicsService.subjects(WebRequest.of(query));

            Map<String, Object> overview = mapValue(dashboard.get("overview"));
            Map<String, Object> financialReport = mapValue(dashboard.get("financialReport"));
            Map<String, Object> orderAnalysis = mapValue(subjects.get("orderAnalysis"));
            Map<String, Object> regionAnalysis = mapValue(subjects.get("regionAnalysis"));
            Map<String, Object> orderSummary = mapValue(orderAnalysis.get("summary"));
            Map<String, Object> regionSummary = mapValue(regionAnalysis.get("summary"));

            PeriodicReport report = existing == null ? new PeriodicReport() : existing;
            report.setReportType(period.getReportType());
            report.setPeriodKey(period.getPeriodKey());
            report.setPeriodStartTime(period.getStartTime());
            report.setPeriodEndTime(period.getEndTime());
            report.setReportTitle(period.getReportTitle());
            report.setTotalRevenue(centsToYuan(overview.get("totalRevenueCents")));
            report.setSalesCount(intValue(overview.get("totalOrders")));
            report.setProfitAmount(centsToYuan(overview.get("totalPaidCents")));
            report.setLossAmount(BigDecimal.ZERO);
            report.setExtraMetric1(String.valueOf(overview.getOrDefault("activeRentals", 0)));
            report.setExtraMetric2(String.valueOf(orderSummary.getOrDefault("cancelledOrders", 0)));
            report.setExtraMetric3(String.valueOf(regionSummary.getOrDefault("coveredProvinceCount", 0)));
            report.setGenerateMode(existing != null && hasText(existing.getGenerateMode()) ? existing.getGenerateMode() : generateMode);
            report.setGeneratedAt(new Date());
            Map<String, Object> content = new LinkedHashMap<>();
            PeriodicReportListVo summary = toListVo(report);
            content.put("summaryCards", buildSummaryCards(summary));
            content.put("trendRows", buildTrendRows(dashboard));
            content.put("keySections", buildKeySections(summary, dashboard, subjects, period));
            content.put("generatedAt", report.getGeneratedAt());
            report.setContentJson(objectMapper.writeValueAsString(content));
            if (existing == null) {
                periodicReportMapper.insert(report);
                notifyReportCreated(report, period);
            } else {
                periodicReportMapper.updateById(report);
            }
            return report;
        } catch (Exception e) {
            log.error("生成支付宝租赁周期报表失败: {} {}", period.getReportType(), period.getPeriodKey(), e);
            throw new IllegalStateException("生成报表失败");
        }
    }

    /**
     * 支付宝租赁报表成功落库后发送飞书卡片，沿用当前列表摘要里的收入、订单和省份覆盖等口径。
     */
    private void notifyReportCreated(PeriodicReport report, ReportPeriod period) {
        Map<String, String> metrics = new LinkedHashMap<>();
        metrics.put("总收入", report.getTotalRevenue() == null ? "0.00" : report.getTotalRevenue().toPlainString());
        metrics.put("订单数", String.valueOf(report.getSalesCount() == null ? 0 : report.getSalesCount()));
        metrics.put("实付金额", report.getProfitAmount() == null ? "0.00" : report.getProfitAmount().toPlainString());
        metrics.put("活跃租赁", report.getExtraMetric1());
        metrics.put("取消订单", report.getExtraMetric2());
        metrics.put("覆盖省份", report.getExtraMetric3());
        feishuNotifyService.sendPeriodicReportCard(
                "alipay",
                "支付宝租赁",
                reportTypeLabel(report.getReportType()),
                report.getPeriodKey(),
                formatPeriodRange(period.getStartTime(), period.getEndTime()),
                generateModeLabel(report.getGenerateMode()),
                report.getGeneratedAt(),
                metrics,
                feishuNotifyProperties.getReportLinks().getAlipay()
        );
    }

    /**
     * 将实体映射为列表展示对象，避免前端直接解析原始实体。
     */
    private PeriodicReportListVo toListVo(PeriodicReport report) {
        PeriodicReportListVo vo = new PeriodicReportListVo();
        BeanUtils.copyProperties(report, vo);
        vo.setActiveRentals(report.getExtraMetric1());
        vo.setCancelledOrders(report.getExtraMetric2());
        vo.setCoveredProvinceCount(report.getExtraMetric3());
        return vo;
    }

    /**
     * 将数据库中保存的 JSON 快照反序列化为详情页使用的 Map 结构。
     */
    private Map<String, Object> readContent(String contentJson) {
        if (!hasText(contentJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(contentJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignore) {
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> buildSummaryCards(PeriodicReportListVo summary) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card("经营收入", yuanText(summary.getTotalRevenue()), "周期总收入", "money"));
        cards.add(card("实收金额", yuanText(summary.getProfitAmount()), "实际入账金额", "success"));
        cards.add(card("订单规模", summary.getSalesCount() == null ? 0 : summary.getSalesCount(), "周期订单数", "order"));
        cards.add(card("履约风险", textValue(summary.getActiveRentals()) + " / " + textValue(summary.getCancelledOrders()), "活跃租赁 / 取消订单", "risk"));
        return cards;
    }

    private List<Map<String, Object>> buildKeySections(PeriodicReportListVo summary) {
        return buildKeySections(summary, null, null, null);
    }

    private List<Map<String, Object>> buildKeySections(PeriodicReportListVo summary, Map<String, Object> dashboard,
                                                       Map<String, Object> subjects, ReportPeriod period) {
        List<Map<String, Object>> sections = new ArrayList<>();
        Map<String, Object> overview = mapValue(dashboard == null ? null : dashboard.get("overview"));
        Map<String, Object> comparison = mapValue(dashboard == null ? null : dashboard.get("comparison"));
        Map<String, Object> financialReport = mapValue(dashboard == null ? null : dashboard.get("financialReport"));
        Map<String, Object> orderSummary = nestedMap(subjects, "orderAnalysis", "summary");
        Map<String, Object> revenueSummary = nestedMap(subjects, "revenueAnalysis", "summary");
        Map<String, Object> rentalSummary = nestedMap(subjects, "rentalAnalysis", "summary");
        Map<String, Object> regionSummary = nestedMap(subjects, "regionAnalysis", "summary");
        Map<String, Object> deviceSummary = nestedMap(subjects, "deviceAnalysis", "summary");

        List<Map<String, Object>> executiveRows = new ArrayList<>();
        executiveRows.add(row("报表定位", cadenceFocus(summary.getReportType()), "周/月/年复用同一经营复盘骨架，口径保持一致", "stable", null));
        executiveRows.add(row("统计周期", period == null ? textValue(summary.getPeriodKey()) : formatPeriodRange(period.getStartTime(), period.getEndTime()), "只统计已结束周期，避免动态数据影响复盘", "stable", null));
        executiveRows.add(row("经营结论", buildExecutiveConclusion(summary), "先看收入、实收、订单和履约状态", riskStatus(summary), null));
        sections.add(section("管理摘要", executiveRows));

        List<Map<String, Object>> kpiRows = new ArrayList<>();
        kpiRows.add(row("经营收入", yuanText(summary.getTotalRevenue()), "周期订单总金额", "normal", "判断平台租赁成交体量"));
        kpiRows.add(row("实收金额", yuanText(summary.getProfitAmount()), "周期内实际支付金额", "normal", "与总收入共同判断收款质量"));
        kpiRows.add(row("订单规模", summary.getSalesCount() == null ? 0 : summary.getSalesCount(), "周期订单数", "normal", "用于判断需求强弱"));
        kpiRows.add(row("完结率", rateText(orderSummary.get("finishedRate")), "已完结订单占比", rateStatus(orderSummary.get("finishedRate")), "完结率异常时优先查履约节点"));
        kpiRows.add(row("地域覆盖", textValue(summary.getCoveredProvinceCount()), "成交省份数量", "normal", "用于判断获客地域是否集中"));
        sections.add(section("核心KPI", kpiRows));

        List<Map<String, Object>> comparisonRows = new ArrayList<>();
        comparisonRows.add(comparisonRow("经营收入", yuanText(summary.getTotalRevenue()), centsToYuanText(comparison.get("previousRevenueCents")), "-",
                firstValue(revenueSummary.get("totalRevenueMoM"), comparison.get("revenueChange")), revenueSummary.get("totalRevenueYoY"),
                "收入变化用于判断商品、渠道和流量策略是否有效"));
        comparisonRows.add(comparisonRow("实收金额", yuanText(summary.getProfitAmount()), "-", "-", revenueSummary.get("totalPaidMoM"),
                revenueSummary.get("totalPaidYoY"), "实收变化用于判断支付转化与履约推进质量"));
        comparisonRows.add(comparisonRow("订单规模", summary.getSalesCount() == null ? 0 : summary.getSalesCount(), comparison.get("previousOrders"), "-",
                firstValue(orderSummary.get("totalOrdersMoM"), comparison.get("ordersChange")), orderSummary.get("totalOrdersYoY"),
                "订单变化用于判断需求端波动"));
        comparisonRows.add(comparisonRow("活跃租赁", textValue(summary.getActiveRentals()), "-", "-", rentalSummary.get("activeOrdersMoM"),
                rentalSummary.get("activeOrdersYoY"), "活跃租赁是未来履约和回款的前置信号"));
        sections.add(section("差异对比", comparisonRows));

        List<Map<String, Object>> businessRows = buildBusinessBreakdownRows(financialReport, regionSummary, deviceSummary);
        if (!businessRows.isEmpty()) {
            sections.add(section("业务拆解", businessRows));
        }

        List<Map<String, Object>> fulfillRows = new ArrayList<>();
        fulfillRows.add(row("活跃租赁", textValue(summary.getActiveRentals()), "仍在履约中的订单", "normal", "关注到期归还和逾期风险"));
        fulfillRows.add(row("取消订单", textValue(summary.getCancelledOrders()), "取消会直接影响成交质量", intValue(summary.getCancelledOrders()) > 0 ? "warning" : "stable", "取消原因应回流到商品、价格和履约配置"));
        fulfillRows.add(row("逾期订单", overview.getOrDefault("overdueCount", 0), "仍需处理的履约风险", intValue(overview.get("overdueCount")) > 0 ? "warning" : "stable", "逾期订单优先进入人工跟进清单"));
        sections.add(section("风险预警", fulfillRows));

        List<Map<String, Object>> actionRows = new ArrayList<>();
        actionRows.add(row("收入动作", revenueAction(summary), "下周期经营动作", "action", null));
        actionRows.add(row("履约动作", fulfillmentAction(summary, overview), "风险闭环动作", "action", null));
        actionRows.add(row("地域动作", regionAction(summary.getCoveredProvinceCount()), "市场覆盖动作", "action", null));
        sections.add(section("行动建议", actionRows));

        List<Map<String, Object>> noteRows = new ArrayList<>();
        noteRows.add(row("金额口径", "总收入、实收金额沿用支付宝租赁经营看板口径", "后端从分统一换算为元", "stable", null));
        noteRows.add(row("对比口径", "环比=上一等长周期，同比=去年同期", "无历史数据时显示为 -", "stable", null));
        sections.add(section("数据说明", noteRows));
        return sections;
    }

    private List<Map<String, Object>> buildTrendRows(Map<String, Object> dashboard) {
        Map<String, Object> financialReport = mapValue(dashboard.get("financialReport"));
        List<Map<String, Object>> sourceRows = extractRows(financialReport.get("trendRows"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : sourceRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", item.getOrDefault("date", item.getOrDefault("label", "-")));
            row.put("amount", centsToYuan(item.get("amountCents")));
            row.put("count", intValue(item.get("orderCount")));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> buildTrendRows(PeriodicReport report, PeriodicReportListVo summary) {
        if (report.getPeriodStartTime() != null && report.getPeriodEndTime() != null) {
            try {
                Map<String, Object> query = new LinkedHashMap<>();
                query.put("rangeType", "CUSTOM");
                query.put("timezone", "Asia/Shanghai");
                query.put("startDate", DATE_FMT.format(report.getPeriodStartTime().toInstant().atZone(ZONE_ID).toLocalDate()));
                query.put("endDate", DATE_FMT.format(report.getPeriodEndTime().toInstant().atZone(ZONE_ID).toLocalDate()));
                query.put("topN", 20);
                List<Map<String, Object>> rebuiltRows = buildTrendRows(webAnalyticsService.dashboard(WebRequest.of(query)));
                if (!rebuiltRows.isEmpty()) {
                    return rebuiltRows;
                }
            } catch (Exception e) {
                log.warn("回填支付宝租赁报表趋势失败: {} {}", report.getId(), report.getPeriodKey(), e);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", hasText(summary.getPeriodKey()) ? summary.getPeriodKey() : "本期");
        row.put("amount", summary.getTotalRevenue() == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : summary.getTotalRevenue());
        row.put("count", summary.getSalesCount() == null ? 0 : summary.getSalesCount());
        rows.add(row);
        return rows;
    }

    private List<Map<String, Object>> normalizeTrendRows(List<Map<String, Object>> sourceRows) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : sourceRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            Object label = firstPresent(item.get("label"), item.get("date"), item.get("periodKey"), item.get("name"));
            row.put("label", label == null ? "-" : label);
            row.put("amount", trendAmount(item));
            row.put("count", intValue(firstPresent(item.get("count"), item.get("orderCount"), item.get("orders"))));
            rows.add(row);
        }
        return rows;
    }

    private Object trendAmount(Map<String, Object> row) {
        Object amount = firstPresent(row.get("amount"), row.get("amountYuan"), row.get("orderAmount"), row.get("totalAmount"));
        if (amount != null) {
            return amount;
        }
        if (row.containsKey("amountCents")) {
            return centsToYuan(row.get("amountCents"));
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Object value) {
        if (value instanceof List) {
            return (List<Map<String, Object>>) value;
        }
        return new ArrayList<>();
    }

    private Map<String, Object> card(String label, Object value, String desc, String tone) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("label", label);
        card.put("value", value == null ? "-" : value);
        card.put("desc", desc);
        card.put("tone", tone);
        return card;
    }

    private Map<String, Object> section(String title, List<Map<String, Object>> rows) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("title", title);
        section.put("rows", rows);
        return section;
    }

    private Map<String, Object> row(String name, Object value) {
        return row(name, value, null, null, null);
    }

    private Map<String, Object> row(String name, Object value, String desc, String status, String insight) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("value", value == null ? "-" : value);
        if (hasText(desc)) {
            row.put("desc", desc);
        }
        if (hasText(status)) {
            row.put("status", status);
        }
        if (hasText(insight)) {
            row.put("insight", insight);
        }
        return row;
    }

    private Map<String, Object> comparisonRow(String name, Object value, Object previous, Object samePeriodLastYear,
                                              Object mom, Object yoy, String insight) {
        Map<String, Object> row = row(name, value, "本期 / 上期 / 去年同期", changeStatus(mom), insight);
        row.put("previous", emptyToDash(previous));
        row.put("samePeriodLastYear", emptyToDash(samePeriodLastYear));
        row.put("mom", formatChange(mom));
        row.put("yoy", formatChange(yoy));
        return row;
    }

    private String yuanText(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String centsToYuanText(Object cents) {
        if (cents == null || !hasText(String.valueOf(cents))) {
            return "-";
        }
        try {
            return centsToYuan(cents).toPlainString();
        } catch (Exception ignore) {
            return "-";
        }
    }

    private String amountText(Object value) {
        if (value == null || !hasText(String.valueOf(value))) {
            return "-";
        }
        return BigDecimal.valueOf(numberValue(value)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String rateText(Object value) {
        if (value == null || !hasText(String.valueOf(value))) {
            return "-";
        }
        String text = String.valueOf(value);
        if (text.contains("%")) {
            return text;
        }
        return BigDecimal.valueOf(numberValue(value)).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String formatChange(Object value) {
        if (value == null || !hasText(String.valueOf(value))) {
            return "-";
        }
        double number = numberValue(value);
        return (number > 0D ? "+" : "") + BigDecimal.valueOf(number).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private Object emptyToDash(Object value) {
        return value == null || !hasText(String.valueOf(value)) ? "-" : value;
    }

    private Object firstValue(Object primary, Object fallback) {
        return primary == null || !hasText(String.valueOf(primary)) ? fallback : primary;
    }

    private Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null && hasText(String.valueOf(value))) {
                return value;
            }
        }
        return null;
    }

    private String changeStatus(Object value) {
        double number = numberValue(value);
        if (number > 0D) {
            return "up";
        }
        if (number < 0D) {
            return "down";
        }
        return "stable";
    }

    private String rateStatus(Object value) {
        return numberValue(value) < 80D ? "warning" : "stable";
    }

    private String riskStatus(PeriodicReportListVo summary) {
        return intValue(summary.getCancelledOrders()) > 0 ? "warning" : "stable";
    }

    private String buildExecutiveConclusion(PeriodicReportListVo summary) {
        if ((summary.getTotalRevenue() == null ? BigDecimal.ZERO : summary.getTotalRevenue()).compareTo(BigDecimal.ZERO) <= 0) {
            return "本周期暂无有效租赁收入，优先确认订单数据和统计范围。";
        }
        String risk = intValue(summary.getCancelledOrders()) > 0 ? "存在取消订单，需要复盘取消原因。" : "取消风险可控。";
        return "本周期收入 " + yuanText(summary.getTotalRevenue()) + "，实收 " + yuanText(summary.getProfitAmount())
                + "，订单 " + (summary.getSalesCount() == null ? 0 : summary.getSalesCount()) + " 单，" + risk;
    }

    private String cadenceFocus(String reportType) {
        if ("WEEK".equalsIgnoreCase(reportType)) {
            return "周报关注短期订单脉搏、履约异常和下周动作。";
        }
        if ("YEAR".equalsIgnoreCase(reportType)) {
            return "年报关注全年成交、收款、地域和履约质量变化。";
        }
        return "月报关注经营结果、差异原因、风险预警和下月计划。";
    }

    private String revenueAction(PeriodicReportListVo summary) {
        if ((summary.getTotalRevenue() == null ? BigDecimal.ZERO : summary.getTotalRevenue()).compareTo(BigDecimal.ZERO) <= 0) {
            return "先核对订单同步、支付状态和统计时间范围，确认是否存在漏数。";
        }
        return "复盘成交贡献最高的设备和地区，保留下周期可复制的商品和投放动作。";
    }

    private String fulfillmentAction(PeriodicReportListVo summary, Map<String, Object> overview) {
        int riskCount = intValue(summary.getCancelledOrders()) + intValue(overview.get("overdueCount"));
        if (riskCount <= 0) {
            return "保持订单取消和逾期日清，避免履约风险跨周期累积。";
        }
        return "对 " + riskCount + " 个取消/逾期风险项补齐原因、责任人和处理时限。";
    }

    private String regionAction(String coveredProvinceCount) {
        if (intValue(coveredProvinceCount) <= 1) {
            return "地域覆盖偏集中，建议结合设备供给和投放渠道扩大可成交区域。";
        }
        return "保留高成交省份投放策略，同时跟踪低成交地区的转化原因。";
    }

    private Map<String, Object> nestedMap(Map<String, Object> source, String sectionKey, String nestedKey) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        return mapValue(mapValue(source.get(sectionKey)).get(nestedKey));
    }

    private List<Map<String, Object>> buildBusinessBreakdownRows(Map<String, Object> financialReport,
                                                                 Map<String, Object> regionSummary,
                                                                 Map<String, Object> deviceSummary) {
        List<Map<String, Object>> rows = new ArrayList<>();
        appendRankRows(rows, "热门设备", extractRows(financialReport.get("deviceRows")), "name", "amountCents", "orderCount", true);
        appendRankRows(rows, "成交地区", extractRows(financialReport.get("regionRows")), "name", "amount", "orderCount", false);
        rows.add(row("覆盖省份", regionSummary.getOrDefault("coveredProvinceCount", 0), "成交地域覆盖", "normal", "用于判断区域集中度"));
        rows.add(row("覆盖设备", deviceSummary.getOrDefault("coveredDeviceCount", 0), "产生订单的设备数", "normal", "用于判断商品结构宽度"));
        return rows;
    }

    private void appendRankRows(List<Map<String, Object>> target, String prefix, List<Map<String, Object>> rows,
                                String nameKey, String amountKey, String countKey, boolean amountInCents) {
        int count = 0;
        for (Map<String, Object> item : rows) {
            if (count >= 3) {
                break;
            }
            target.add(row(prefix + " - " + item.getOrDefault(nameKey, "未命名"),
                    amountInCents ? centsToYuanText(item.get(amountKey)) : amountText(item.get(amountKey)),
                    "订单数 " + item.getOrDefault(countKey, 0),
                    "normal",
                    "Top 结构用于识别主要增长来源"));
            count++;
        }
    }

    private String textValue(String value) {
        return hasText(value) ? value : "0";
    }

    @SuppressWarnings("unchecked")
    /**
     * 安全读取嵌套分析块，避免空对象导致的类型转换异常。
     */
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<>();
    }

    /**
     * 支付宝租赁分析金额多数以分返回，这里统一转换为元。
     */
    private BigDecimal centsToYuan(Object cents) {
        if (cents == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long value = cents instanceof Number ? ((Number) cents).longValue() : Long.parseLong(String.valueOf(cents));
        return BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * 兼容不同聚合结果里的整数类型返回值。
     */
    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignore) {
            return 0;
        }
    }

    private double numberValue(Object value) {
        if (value == null) {
            return 0D;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).replace("%", "").replace(",", "").trim());
        } catch (Exception ignore) {
            return 0D;
        }
    }

    private String reportTypeLabel(String reportType) {
        if ("WEEK".equalsIgnoreCase(reportType)) {
            return "周报";
        }
        if ("MONTH".equalsIgnoreCase(reportType)) {
            return "月报";
        }
        if ("YEAR".equalsIgnoreCase(reportType)) {
            return "年报";
        }
        return reportType;
    }

    private String generateModeLabel(String generateMode) {
        if ("AUTO".equalsIgnoreCase(generateMode)) {
            return "自动生成";
        }
        if ("MANUAL".equalsIgnoreCase(generateMode)) {
            return "手动补生成";
        }
        return generateMode;
    }

    private String formatPeriodRange(Date startTime, Date endTime) {
        return DATE_FMT.format(startTime.toInstant().atZone(ZONE_ID).toLocalDate())
                + " ~ "
                + DATE_FMT.format(endTime.toInstant().atZone(ZONE_ID).toLocalDate());
    }

    private boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * 只允许对已结束周期生成正式报表，避免当前周期数据波动影响复盘。
     */
    private void assertFinishedPeriod(ReportPeriod period) {
        if (!period.getEndTime().before(new Date())) {
            throw new IllegalArgumentException("只能生成已结束周期的报表");
        }
    }

    /**
     * 根据目标日期定位其所属自然周、自然月或自然年。
     */
    private ReportPeriod resolvePeriod(String reportType, Date targetDate) {
        LocalDate localDate = targetDate.toInstant().atZone(ZONE_ID).toLocalDate();
        if ("WEEK".equalsIgnoreCase(reportType)) {
            return weekPeriod(localDate);
        }
        if ("MONTH".equalsIgnoreCase(reportType)) {
            return monthPeriod(localDate.withDayOfMonth(1));
        }
        if ("YEAR".equalsIgnoreCase(reportType)) {
            return yearPeriod(localDate.withDayOfYear(1));
        }
        throw new IllegalArgumentException("不支持的报表类型: " + reportType);
    }

    private ReportPeriod previousWeekPeriod() {
        return weekPeriod(LocalDate.now(ZONE_ID).minusWeeks(1));
    }

    private ReportPeriod previousMonthPeriod() {
        return monthPeriod(LocalDate.now(ZONE_ID).minusMonths(1).withDayOfMonth(1));
    }

    private ReportPeriod previousYearPeriod() {
        return yearPeriod(LocalDate.now(ZONE_ID).minusYears(1).withDayOfYear(1));
    }

    private ReportPeriod weekPeriod(LocalDate date) {
        LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);
        return new ReportPeriod(
                "WEEK",
                start.getYear() + "-W" + String.format("%02d", start.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)),
                toDate(start.atTime(LocalTime.MIN)),
                toDate(end.atTime(LocalTime.MAX)),
                start.format(DATE_FMT),
                end.format(DATE_FMT),
                "支付宝租赁周报-" + start + "至" + end
        );
    }

    private ReportPeriod monthPeriod(LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.lengthOfMonth());
        return new ReportPeriod(
                "MONTH",
                start.format(MONTH_FMT),
                toDate(start.atTime(LocalTime.MIN)),
                toDate(end.atTime(LocalTime.MAX)),
                start.format(DATE_FMT),
                end.format(DATE_FMT),
                "支付宝租赁月报-" + start.format(MONTH_FMT)
        );
    }

    private ReportPeriod yearPeriod(LocalDate date) {
        LocalDate start = date.withDayOfYear(1);
        LocalDate end = date.withDayOfYear(date.lengthOfYear());
        return new ReportPeriod(
                "YEAR",
                start.format(YEAR_FMT),
                toDate(start.atTime(LocalTime.MIN)),
                toDate(end.atTime(LocalTime.MAX)),
                start.format(DATE_FMT),
                end.format(DATE_FMT),
                "支付宝租赁年报-" + start.format(YEAR_FMT)
        );
    }

    private Date toDate(java.time.LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZONE_ID).toInstant());
    }

    /**
     * 统一封装报表周期边界、标题与用于查询分析接口的日期字符串。
     */
    private static class ReportPeriod {
        private final String reportType;
        private final String periodKey;
        private final Date startTime;
        private final Date endTime;
        private final String startDate;
        private final String endDate;
        private final String reportTitle;

        private ReportPeriod(String reportType, String periodKey, Date startTime, Date endTime, String startDate, String endDate, String reportTitle) {
            this.reportType = reportType;
            this.periodKey = periodKey;
            this.startTime = startTime;
            this.endTime = endTime;
            this.startDate = startDate;
            this.endDate = endDate;
            this.reportTitle = reportTitle;
        }

        public String getReportType() { return reportType; }
        public String getPeriodKey() { return periodKey; }
        public Date getStartTime() { return startTime; }
        public Date getEndTime() { return endTime; }
        public String getStartDate() { return startDate; }
        public String getEndDate() { return endDate; }
        public String getReportTitle() { return reportTitle; }
    }
}

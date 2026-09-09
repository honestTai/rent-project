package com.fly.rent.report.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 周期报表详情对象。
 */
@Data
public class PeriodicReportDetailVo {
    private PeriodicReportListVo summary;
    private List<Map<String, Object>> summaryCards;
    private List<Map<String, Object>> trendRows;
    private List<Map<String, Object>> keySections;
}

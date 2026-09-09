package com.fly.rent.report.service;

import com.fly.rent.report.dto.PeriodicReportGenerateDto;
import com.fly.rent.report.dto.PeriodicReportQueryDto;
import com.fly.rent.report.entity.PeriodicReport;
import com.fly.rent.report.vo.PeriodicReportDetailVo;
import com.fly.rent.report.vo.PeriodicReportListVo;

import java.util.List;

/**
 * 周期报表服务。
 */
public interface PeriodicReportService {
    List<PeriodicReportListVo> listReports(PeriodicReportQueryDto queryDto);

    PeriodicReportDetailVo getReportDetail(Integer id);

    PeriodicReport generateReport(PeriodicReportGenerateDto generateDto);

    void generatePreviousWeekReport();

    void generatePreviousMonthReport();

    void generatePreviousYearReport();
}

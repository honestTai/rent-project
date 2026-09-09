package com.fly.rent.report.controller;

import com.fly.rent.entity.Result;
import com.fly.rent.report.dto.PeriodicReportGenerateDto;
import com.fly.rent.report.dto.PeriodicReportQueryDto;
import com.fly.rent.report.service.PeriodicReportService;
import com.fly.rent.web.support.AbstractWebController;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 支付宝租赁周期报表中心接口。
 * 保持 /api/web 前缀命中支付宝租赁服务，但从 analytics 子域下放为独立报表模块。
 */
@RestController
@RequestMapping("/api/web/report-center")
@RequiredArgsConstructor
public class WebPeriodicReportController extends AbstractWebController {
    private final PeriodicReportService periodicReportService;

    @PostMapping("/page")
    /**
     * 查询周期报表列表。
     */
    public Result page(@RequestBody(required = false) PeriodicReportQueryDto queryDto) {
        return success(periodicReportService.listReports(queryDto));
    }

    @GetMapping("/{id}")
    /**
     * 查询单条周期报表详情。
     */
    public Result detail(@PathVariable Integer id) {
        return success(periodicReportService.getReportDetail(id));
    }

    @PostMapping("/generate")
    /**
     * 手动补生成历史周期报表。
     */
    public Result generate(@RequestBody PeriodicReportGenerateDto generateDto) {
        return success(periodicReportService.generateReport(generateDto));
    }
}

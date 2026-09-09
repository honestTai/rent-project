package com.fly.rent.web.analytics;

import com.fly.rent.web.support.AbstractWebController;
import com.fly.rent.entity.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Web 兼容层数据统计与报表接口。
 * 提供仪表盘汇总数据（订单趋势、设备排行、状态与时段分布等）。
 */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebAnalyticsController extends AbstractWebController {

    private final WebAnalyticsService analyticsService;
    private final WebAnalyticsTopicsService analyticsTopicsService;

    /**
     * 仪表盘数据。
     * 支持 timezone、startDate/endDate、rangeType 等参数，
     * 返回 overview、comparison、trend、rankings、distributions、insights、meta。
     *
     * @param body 请求体（可选）
     * @return 仪表盘聚合数据
     */
    @PostMapping("/analytics/dashboard")
    public Result dashboard(@RequestBody(required = false) Map<String, Object> body) {
        return success(analyticsService.dashboard(request(body)));
    }

    @PostMapping("/analytics/subjects")
    public Result subjects(@RequestBody(required = false) Map<String, Object> body) {
        return success(analyticsTopicsService.subjects(request(body)));
    }
}

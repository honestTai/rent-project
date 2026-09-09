package com.fly.rent.web.log;

import com.fly.rent.web.support.AbstractWebController;
import com.fly.rent.entity.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Web 兼容层操作日志管理接口。
 * 提供操作日志分页查询、按订单 ID 查询、按订单号查询共 3 个 POST 接口，
 * 统一挂在 {@code /api/web} 路径下。
 *
 * @author HonestTat
 * @since 2026-03-12
 */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebOperLogController extends AbstractWebController {

    private final WebOperLogService operLogService;

    /**
     * 操作日志分页查询。
     * 支持按 orderNo（模糊）、orderId、operType、operResult（SUCCESS/FAIL）筛选。
     * 返回字段已映射为前端期望格式。
     *
     * @param body 请求体，包含分页参数与筛选条件
     * @return 分页结果
     */
    @PostMapping("/order-oper-logs/page")
    public Result pageOperLogs(@RequestBody(required = false) Map<String, Object> body) {
        return operLogService.pageOperLogs(request(body));
    }

    /**
     * 按订单 ID 查询操作日志。
     * 返回指定订单的所有操作日志，按时间降序排列。
     *
     * @param body 请求体，包含 orderId
     * @return 日志列表
     */
    @PostMapping("/order-oper-logs/by-order-id")
    public Result listByOrderId(@RequestBody(required = false) Map<String, Object> body) {
        return operLogService.listByOrderId(request(body));
    }

    /**
     * 按订单号查询操作日志。
     * 返回指定订单号的所有操作日志，按时间降序排列。
     *
     * @param body 请求体，包含 orderNo
     * @return 日志列表
     */
    @PostMapping("/order-oper-logs/by-order-no")
    public Result listByOrderNo(@RequestBody(required = false) Map<String, Object> body) {
        return operLogService.listByOrderNo(request(body));
    }
}

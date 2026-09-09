package com.fly.rent.web.log;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fly.rent.web.support.WebRequest;
import com.fly.rent.web.support.WebResponseUtil;
import com.fly.rent.entity.OrderOperLog;
import com.fly.rent.entity.Result;
import com.fly.rent.mapper.OrderOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Web 兼容层操作日志服务。
 * 负责操作日志的分页查询、按订单 ID 查询、按订单号查询。
 * <p>
 * 返回时做字段映射：后端 {@link OrderOperLog} → 前端期望格式。
 *
 * @author HonestTat
 * @since 2026-03-12
 */
@Service
@RequiredArgsConstructor
public class WebOperLogService {

    private final OrderOperLogMapper operLogMapper;

    /**
     * 操作日志分页查询。
     * <p>
     * 支持筛选条件：
     * <ul>
     *   <li>orderNo — 订单号，模糊匹配</li>
     *   <li>orderId — 订单 ID，精确匹配</li>
     *   <li>operType — 操作类型，精确匹配</li>
     *   <li>operResult — SUCCESS 对应 success=1，FAIL 对应 success=0</li>
     * </ul>
     * 返回字段映射为前端期望格式：logId / operSource / operResult / statusBefore / statusAfter / operTime / errorMsg / requestData / responseCode。
     *
     * @param req 请求参数
     * @return 分页结果，记录已做字段映射
     */
    public Result pageOperLogs(WebRequest req) {
        QueryWrapper<OrderOperLog> qw = new QueryWrapper<>();

        if (req.hasText("orderNo")) {
            qw.like("order_no", req.text("orderNo"));
        }
        if (req.hasText("orderId")) {
            qw.eq("order_id", req.integer("orderId"));
        }
        if (req.hasText("operType")) {
            qw.eq("oper_type", req.text("operType"));
        }
        if (req.hasText("operator")) {
            qw.eq("operator", req.text("operator"));
        }
        if (req.hasText("operResult")) {
            String operResult = req.text("operResult");
            if ("SUCCESS".equalsIgnoreCase(operResult)) {
                qw.eq("success", 1);
            } else if ("FAIL".equalsIgnoreCase(operResult)) {
                qw.eq("success", 0);
            }
        }
        qw.orderByDesc("created_at");

        Page<OrderOperLog> page = new Page<>(req.page(), req.limit());
        operLogMapper.selectPage(page, qw);
        List<OrderOperLog> entities = page.getRecords();

        List<Map<String, Object>> records = entities.stream()
                .map(this::mapOperLogToFrontend)
                .collect(Collectors.toList());

        return WebResponseUtil.page(records, page.getTotal());
    }

    /**
     * 按订单 ID 查询操作日志。
     * <p>
     * 返回指定 orderId 的全部日志，按创建时间降序排列，字段已做映射。
     *
     * @param req 请求参数，包含 orderId
     * @return 日志列表
     */
    public Result listByOrderId(WebRequest req) {
        Integer orderId = req.integer("orderId");
        if (orderId == null) {
            return WebResponseUtil.error(400, "orderId 不能为空");
        }

        QueryWrapper<OrderOperLog> qw = new QueryWrapper<>();
        qw.eq("order_id", orderId);
        qw.orderByDesc("created_at");

        List<OrderOperLog> list = operLogMapper.selectList(qw);

        List<Map<String, Object>> records = list.stream()
                .map(this::mapOperLogToFrontend)
                .collect(Collectors.toList());

        return WebResponseUtil.success(records);
    }

    /**
     * 按订单号查询操作日志。
     * <p>
     * 返回指定 orderNo 的全部日志，按创建时间降序排列，字段已做映射。
     *
     * @param req 请求参数，包含 orderNo
     * @return 日志列表
     */
    public Result listByOrderNo(WebRequest req) {
        String orderNo = req.text("orderNo");
        if (orderNo == null || orderNo.trim().isEmpty()) {
            return WebResponseUtil.error(400, "orderNo 不能为空");
        }

        QueryWrapper<OrderOperLog> qw = new QueryWrapper<>();
        qw.eq("order_no", orderNo.trim());
        qw.orderByDesc("created_at");

        List<OrderOperLog> list = operLogMapper.selectList(qw);

        List<Map<String, Object>> records = list.stream()
                .map(this::mapOperLogToFrontend)
                .collect(Collectors.toList());

        return WebResponseUtil.success(records);
    }

    /**
     * 将后端 OrderOperLog 映射为前端期望的字段格式。
     * <pre>
     * logId         ← orderOperLogId
     * orderId       ← orderId
     * orderNo       ← orderNo
     * operType      ← operType
     * operDesc      ← operDesc
     * operSource    ← operator
     * operResult    ← success==1 ? "SUCCESS" : "FAIL"
     * statusBefore  ← beforeStatus
     * statusAfter   ← afterStatus
     * operTime      ← createtime
     * errorMsg      ← failReason
     * requestData   ← requestBody
     * responseCode  ← 从 resultBody 中提取（如有）
     * </pre>
     */
    private Map<String, Object> mapOperLogToFrontend(OrderOperLog log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("logId", log.getOrderOperLogId());
        map.put("orderId", log.getOrderId());
        map.put("orderNo", log.getOrderNo());
        map.put("operType", log.getOperType());
        map.put("operDesc", log.getOperDesc());
        map.put("operSource", log.getOperator());
        map.put("operResult", log.getSuccess() != null && log.getSuccess() == 1 ? "SUCCESS" : "FAIL");
        map.put("statusBefore", log.getBeforeStatus());
        map.put("statusAfter", log.getAfterStatus());
        map.put("operTime", log.getCreatetime());
        map.put("errorMsg", log.getFailReason());
        map.put("requestData", log.getRequestBody());
        map.put("responseData", log.getResultBody());
        map.put("responseCode", extractResponseCode(log.getResultBody()));
        return map;
    }

    /**
     * 尝试从 resultBody JSON 字符串中提取 code 字段值。
     * 使用简单字符串匹配避免引入额外 JSON 依赖。
     */
    private String extractResponseCode(String resultBody) {
        if (resultBody == null || resultBody.isEmpty()) {
            return null;
        }
        int idx = resultBody.indexOf("\"code\"");
        if (idx < 0) {
            idx = resultBody.indexOf("'code'");
        }
        if (idx < 0) {
            return null;
        }
        int colonIdx = resultBody.indexOf(':', idx);
        if (colonIdx < 0) {
            return null;
        }
        int start = colonIdx + 1;
        while (start < resultBody.length() && (resultBody.charAt(start) == ' ' || resultBody.charAt(start) == '"')) {
            start++;
        }
        int end = start;
        while (end < resultBody.length() && resultBody.charAt(end) != ',' && resultBody.charAt(end) != '}'
                && resultBody.charAt(end) != '"' && resultBody.charAt(end) != ' ') {
            end++;
        }
        if (start < end) {
            return resultBody.substring(start, end);
        }
        return null;
    }
}

package com.fly.rent.web.rent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentDepositDeductRecord;
import com.fly.rent.entity.User;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.RentDepositDeductRecordMapper;
import com.fly.rent.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本地订单风控评分模型。
 * <p>
 * 第一版只读取本地订单和押金扣减台账，输出可解释命中项，辅助运营做人工审核。
 */
@Service
@RequiredArgsConstructor
public class LocalRentRiskModelService {

    private static final long NINETY_DAYS_MILLIS = 90L * 24 * 60 * 60 * 1000;
    private static final int RECENT_LIMIT = 5;

    private static final Set<String> ACTIVE_STATUSES = new HashSet<>(Arrays.asList(
            AlipayRentConstants.STATUS_CREATED,
            AlipayRentConstants.STATUS_SIGNED,
            AlipayRentConstants.STATUS_APPROVED,
            AlipayRentConstants.STATUS_PAID,
            AlipayRentConstants.STATUS_DELIVERED,
            AlipayRentConstants.STATUS_RECEIVED,
            AlipayRentConstants.STATUS_RETURN_DELIVERED,
            AlipayRentConstants.STATUS_RETURN_RECEIVED
    ));

    private static final Set<String> CLOSED_OR_REFUND_STATUSES = new HashSet<>(Arrays.asList(
            AlipayRentConstants.STATUS_CLOSED,
            AlipayRentConstants.STATUS_PENDING_CANCLE
    ));

    private final OrderMapper orderMapper;
    private final RentDepositDeductRecordMapper deductRecordMapper;
    private final UserMapper userMapper;

    public Map<String, Object> evaluate(Order currentOrder) {
        if (currentOrder == null) {
            throw new IllegalArgumentException("order 不能为空");
        }
        List<Order> userOrders = findUserOrders(currentOrder);
        List<Order> historyOrders = excludeCurrentOrder(userOrders, currentOrder);
        List<RentDepositDeductRecord> deductRecords = findDeductRecords(historyOrders);

        OrderStats orderStats = summarizeOrders(historyOrders);
        AftersaleStats aftersaleStats = summarizeDeductRecords(deductRecords);
        List<Map<String, Object>> hitRules = new ArrayList<>();
        int score = score(currentOrder, orderStats, aftersaleStats, hitRules);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", level(score));
        result.put("score", score);
        result.put("summary", summary(orderStats, aftersaleStats));
        result.put("hitRules", hitRules);
        result.put("orderStats", orderStats.toMap());
        result.put("aftersaleStats", aftersaleStats.toMap());
        result.put("recentOrders", recentOrders(historyOrders));
        result.put("recentDeductRecords", recentDeductRecords(deductRecords));
        return result;
    }

    private List<Order> findUserOrders(Order currentOrder) {
        UserIdentity identity = resolveIdentity(currentOrder);
        if (!identity.hasAny()) {
            return Collections.emptyList();
        }
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(identity.userUuid)) {
            wrapper.eq("user_uuid", identity.userUuid);
        } else {
            boolean hasCondition = false;
            if (identity.userId != null) {
                wrapper.eq("user_id", identity.userId);
                hasCondition = true;
            }
            if (StringUtils.hasText(identity.userTel)) {
                if (hasCondition) {
                    wrapper.or();
                }
                wrapper.eq("user_phone", identity.userTel);
            }
        }
        wrapper.orderByDesc("created_at");
        List<Order> orders = orderMapper.selectList(wrapper);
        return orders == null ? Collections.emptyList() : orders;
    }

    private UserIdentity resolveIdentity(Order currentOrder) {
        User user = null;
        if (!StringUtils.hasText(currentOrder.getUserUuid()) && currentOrder.getUserId() != null) {
            user = userMapper.selectById(currentOrder.getUserId());
        }
        UserIdentity identity = new UserIdentity();
        identity.userUuid = StringUtils.hasText(currentOrder.getUserUuid())
                ? currentOrder.getUserUuid().trim()
                : user == null ? null : user.getUuid();
        identity.userId = currentOrder.getUserId();
        identity.userTel = StringUtils.hasText(currentOrder.getUserTel())
                ? currentOrder.getUserTel().trim()
                : user == null ? null : user.getUserTel();
        return identity;
    }

    private List<Order> excludeCurrentOrder(List<Order> orders, Order currentOrder) {
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Order> history = new ArrayList<>();
        for (Order order : orders) {
            if (!sameOrder(order, currentOrder)) {
                history.add(order);
            }
        }
        history.sort(orderCreatedDesc());
        return history;
    }

    private boolean sameOrder(Order left, Order right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getOrderId() != null && right.getOrderId() != null) {
            return left.getOrderId().equals(right.getOrderId());
        }
        return StringUtils.hasText(left.getOrderNo())
                && StringUtils.hasText(right.getOrderNo())
                && left.getOrderNo().equals(right.getOrderNo());
    }

    private List<RentDepositDeductRecord> findDeductRecords(List<Order> historyOrders) {
        if (historyOrders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> orderIds = new ArrayList<>();
        List<String> orderNos = new ArrayList<>();
        for (Order order : historyOrders) {
            if (order.getOrderId() != null) {
                orderIds.add(order.getOrderId());
            }
            if (StringUtils.hasText(order.getOrderNo())) {
                orderNos.add(order.getOrderNo());
            }
        }
        if (orderIds.isEmpty() && orderNos.isEmpty()) {
            return Collections.emptyList();
        }
        QueryWrapper<RentDepositDeductRecord> wrapper = new QueryWrapper<>();
        boolean hasCondition = false;
        if (!orderIds.isEmpty()) {
            wrapper.in("order_id", orderIds);
            hasCondition = true;
        }
        if (!orderNos.isEmpty()) {
            if (hasCondition) {
                wrapper.or();
            }
            wrapper.in("order_no", orderNos);
        }
        wrapper.orderByDesc("created_at");
        List<RentDepositDeductRecord> records = deductRecordMapper.selectList(wrapper);
        return records == null ? Collections.emptyList() : records;
    }

    private OrderStats summarizeOrders(List<Order> historyOrders) {
        OrderStats stats = new OrderStats();
        stats.totalOrders = historyOrders.size();
        for (Order order : historyOrders) {
            String status = order.getAlipayStatus();
            if (AlipayRentConstants.STATUS_FINISHED.equals(status)) {
                stats.finishedOrders++;
            }
            if (ACTIVE_STATUSES.contains(status)) {
                stats.activeOrders++;
            }
            if (CLOSED_OR_REFUND_STATUSES.contains(status)) {
                stats.closedOrRefundingOrders++;
            }
            if (order.getCreatetime() != null && order.getCreatetime() > stats.latestOrderAt) {
                stats.latestOrderAt = order.getCreatetime();
            }
        }
        return stats;
    }

    private AftersaleStats summarizeDeductRecords(List<RentDepositDeductRecord> records) {
        AftersaleStats stats = new AftersaleStats();
        stats.totalRecords = records.size();
        long recentThreshold = System.currentTimeMillis() - NINETY_DAYS_MILLIS;
        for (RentDepositDeductRecord record : records) {
            Integer amount = safeAmount(record.getDeductAmount());
            stats.maxDeductAmount = Math.max(stats.maxDeductAmount, amount);
            if (RentDepositDeductRecord.STATUS_SUCCESS.equals(record.getStatus())) {
                stats.successRecords++;
                stats.successAmount += amount;
            } else if (RentDepositDeductRecord.STATUS_PROCESSING.equals(record.getStatus())) {
                stats.processingRecords++;
                if (isRecent(record, recentThreshold)) {
                    stats.recentRiskRecords++;
                }
            } else if (RentDepositDeductRecord.STATUS_FAILED.equals(record.getStatus())) {
                stats.failedRecords++;
                if (isRecent(record, recentThreshold)) {
                    stats.recentRiskRecords++;
                }
            } else if (RentDepositDeductRecord.STATUS_CANCELLED.equals(record.getStatus())) {
                stats.cancelledRecords++;
            }
            if (record.getCreateTime() != null && record.getCreateTime() > stats.latestDeductAt) {
                stats.latestDeductAt = record.getCreateTime();
            }
        }
        return stats;
    }

    private int score(Order currentOrder, OrderStats orderStats, AftersaleStats aftersaleStats,
                      List<Map<String, Object>> hitRules) {
        int score = 0;
        if (aftersaleStats.successRecords >= 2) {
            score += 45;
            addRule(hitRules, "DEDUCT_HISTORY", "HIGH", "多次历史扣押金记录",
                    "成功扣减 " + aftersaleStats.successRecords + " 次，累计 " + yuan(aftersaleStats.successAmount) + " 元",
                    "审核前重点核对用户历史履约、归还材料和扣款原因。");
        } else if (aftersaleStats.successRecords >= 1) {
            score += 30;
            addRule(hitRules, "DEDUCT_HISTORY", "HIGH", "历史扣押金记录",
                    "成功扣减 " + aftersaleStats.successRecords + " 次，累计 " + yuan(aftersaleStats.successAmount) + " 元",
                    "审核前重点核对该用户上一次扣款原因。");
        }

        int currentDeposit = safeAmount(currentOrder.getOrderDeposit());
        if (currentDeposit > 0 && aftersaleStats.successAmount * 2 >= currentDeposit) {
            score += 25;
            addRule(hitRules, "DEDUCT_AMOUNT_RATIO", "MEDIUM", "历史扣减金额较高",
                    "累计扣减 " + yuan(aftersaleStats.successAmount) + " 元，已达到当前押金 50% 以上",
                    "建议结合设备价值和身份资料决定是否要求补充材料。");
        }

        if (orderStats.activeOrders >= 2) {
            score += 20;
            addRule(hitRules, "CONCURRENT_ACTIVE_ORDERS", "MEDIUM", "存在多笔进行中订单",
                    "历史中仍有 " + orderStats.activeOrders + " 笔进行中订单",
                    "审核前确认用户是否同时占用多台设备。");
        }

        if (orderStats.closedOrRefundingOrders >= 2) {
            score += 15;
            addRule(hitRules, "CLOSED_OR_REFUND_HISTORY", "MEDIUM", "关闭或退款中订单偏多",
                    "历史关闭/退款中订单 " + orderStats.closedOrRefundingOrders + " 笔",
                    "建议复盘取消和退款原因。");
        }

        if (aftersaleStats.recentRiskRecords > 0) {
            score += 15;
            addRule(hitRules, "RECENT_AFTERSALE_UNRESOLVED", "MEDIUM", "近 90 天存在未完全闭环售后",
                    "近 90 天处理中/失败售后 " + aftersaleStats.recentRiskRecords + " 条",
                    "建议先确认售后节点是否已处理完毕。");
        }

        if (orderStats.finishedOrders >= 3 && aftersaleStats.successRecords == 0) {
            score -= 10;
            addRule(hitRules, "GOOD_FINISH_HISTORY", "LOW", "历史履约稳定",
                    "历史完结 " + orderStats.finishedOrders + " 单，暂无成功扣押金记录",
                    "可作为降低风险的参考，但仍需结合当前订单资料审核。");
        }
        return Math.max(0, Math.min(100, score));
    }

    private void addRule(List<Map<String, Object>> hitRules, String code, String level, String title,
                         String evidence, String suggestion) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("code", code);
        rule.put("level", level);
        rule.put("title", title);
        rule.put("evidence", evidence);
        rule.put("suggestion", suggestion);
        hitRules.add(rule);
    }

    private String level(int score) {
        if (score >= 60) {
            return "HIGH";
        }
        if (score >= 30) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String summary(OrderStats orderStats, AftersaleStats aftersaleStats) {
        if (orderStats.totalOrders == 0) {
            return "历史 0 单，暂无本地风险命中。";
        }
        return "历史 " + orderStats.totalOrders + " 单，已完成 " + orderStats.finishedOrders
                + " 单，进行中 " + orderStats.activeOrders + " 单，售后扣减成功 "
                + aftersaleStats.successRecords + " 次，累计扣减 "
                + yuan(aftersaleStats.successAmount) + " 元。";
    }

    private List<Map<String, Object>> recentOrders(List<Order> historyOrders) {
        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (Order order : historyOrders) {
            if (count >= RECENT_LIMIT) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", order.getOrderId());
            item.put("orderNo", order.getOrderNo());
            item.put("alipayStatus", order.getAlipayStatus());
            item.put("goodTitle", order.getGoodTitle());
            item.put("orderTotal", order.getOrderTotal());
            item.put("orderDeposit", order.getOrderDeposit());
            item.put("createdAt", order.getCreatetime());
            result.add(item);
            count++;
        }
        return result;
    }

    private List<Map<String, Object>> recentDeductRecords(List<RentDepositDeductRecord> deductRecords) {
        List<RentDepositDeductRecord> sorted = new ArrayList<>(deductRecords);
        sorted.sort(deductCreatedDesc());
        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (RentDepositDeductRecord record : sorted) {
            if (count >= RECENT_LIMIT) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", record.getId());
            item.put("orderId", record.getOrderId());
            item.put("orderNo", record.getOrderNo());
            item.put("status", record.getStatus());
            item.put("deductAmount", record.getDeductAmount());
            item.put("reasonCode", record.getReasonCode());
            item.put("remark", record.getRemark());
            item.put("createTime", record.getCreateTime());
            result.add(item);
            count++;
        }
        return result;
    }

    private Comparator<Order> orderCreatedDesc() {
        return new Comparator<Order>() {
            @Override
            public int compare(Order left, Order right) {
                return Long.compare(safeLong(right.getCreatetime()), safeLong(left.getCreatetime()));
            }
        };
    }

    private Comparator<RentDepositDeductRecord> deductCreatedDesc() {
        return new Comparator<RentDepositDeductRecord>() {
            @Override
            public int compare(RentDepositDeductRecord left, RentDepositDeductRecord right) {
                return Long.compare(safeLong(right.getCreateTime()), safeLong(left.getCreateTime()));
            }
        };
    }

    private boolean isRecent(RentDepositDeductRecord record, long recentThreshold) {
        return record.getCreateTime() != null && record.getCreateTime() >= recentThreshold;
    }

    private int safeAmount(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String yuan(int cents) {
        return BigDecimal.valueOf(cents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static class UserIdentity {
        private String userUuid;
        private Integer userId;
        private String userTel;

        private boolean hasAny() {
            return StringUtils.hasText(userUuid) || userId != null || StringUtils.hasText(userTel);
        }
    }

    private static class OrderStats {
        private int totalOrders;
        private int finishedOrders;
        private int activeOrders;
        private int closedOrRefundingOrders;
        private long latestOrderAt;

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("totalOrders", totalOrders);
            map.put("finishedOrders", finishedOrders);
            map.put("activeOrders", activeOrders);
            map.put("closedOrRefundingOrders", closedOrRefundingOrders);
            map.put("latestOrderAt", latestOrderAt > 0 ? latestOrderAt : null);
            return map;
        }
    }

    private static class AftersaleStats {
        private int totalRecords;
        private int successRecords;
        private int processingRecords;
        private int failedRecords;
        private int cancelledRecords;
        private int recentRiskRecords;
        private int successAmount;
        private int maxDeductAmount;
        private long latestDeductAt;

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("totalRecords", totalRecords);
            map.put("successRecords", successRecords);
            map.put("processingRecords", processingRecords);
            map.put("failedRecords", failedRecords);
            map.put("cancelledRecords", cancelledRecords);
            map.put("recentRiskRecords", recentRiskRecords);
            map.put("successAmount", successAmount);
            map.put("maxDeductAmount", maxDeductAmount);
            map.put("latestDeductAt", latestDeductAt > 0 ? latestDeductAt : null);
            return map;
        }
    }
}

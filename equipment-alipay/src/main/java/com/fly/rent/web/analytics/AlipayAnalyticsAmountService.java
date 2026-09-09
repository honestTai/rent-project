package com.fly.rent.web.analytics;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentInstallmentInfoEntity;
import com.fly.rent.mapper.RentInstallmentInfoEntityMapper;
import com.fly.rent.support.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 支付宝分析金额口径服务。
 * <p>
 * rent_order.paid_amount 是建单时预估应付金额，不能作为实际已付款口径；
 * 实际租金支付以 installment_plan 中已支付分期金额为准。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayAnalyticsAmountService {

    private static final int ORDER_ID_BATCH_SIZE = 500;
    private static final Set<String> REVENUE_EXCLUDED_STATUSES = new HashSet<>(Arrays.asList(
            AlipayRentConstants.STATUS_CLOSED,
            AlipayRentConstants.STATUS_PENDING_CANCLE,
            "CANCELLED",
            "REFUNDING",
            "REFUNDED"
    ));

    private final RentInstallmentInfoEntityMapper installmentInfoMapper;

    /**
     * 查询订单对应的已支付分期金额，返回单位为分。
     *
     * @param orders 订单列表
     * @return key=orderId, value=已支付分期金额（分）
     */
    public Map<Integer, Long> paidInstallmentAmountCents(List<Order> orders) {
        List<Integer> orderIds = normalizeOrderIds(orders);
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, Long> result = new LinkedHashMap<>();
        for (int start = 0; start < orderIds.size(); start += ORDER_ID_BATCH_SIZE) {
            int end = Math.min(start + ORDER_ID_BATCH_SIZE, orderIds.size());
            List<Integer> batchIds = orderIds.subList(start, end);
            List<RentInstallmentInfoEntity> installments = installmentInfoMapper.selectList(
                    new QueryWrapper<RentInstallmentInfoEntity>()
                            .in("order_id", batchIds)
                            .eq("status", AlipayRentConstants.INSTALLMENT_STATUS_PAID));
            for (RentInstallmentInfoEntity installment : installments) {
                if (installment == null || installment.getOrderId() == null) {
                    continue;
                }
                long amountCents = parseYuanToCents(installment.getInstallmentPrice());
                result.merge(installment.getOrderId(), amountCents, Long::sum);
            }
        }
        return result;
    }

    /**
     * 读取单个订单的已支付分期金额，单位为分。
     */
    public long paidAmountCents(Order order, Map<Integer, Long> paidAmountMap) {
        if (order == null || order.getOrderId() == null || paidAmountMap == null) {
            return 0L;
        }
        return paidAmountMap.getOrDefault(order.getOrderId(), 0L);
    }

    /**
     * 汇总订单列表的已支付分期金额，单位为分。
     */
    public long sumPaidAmountCents(List<Order> orders, Map<Integer, Long> paidAmountMap) {
        if (orders == null || orders.isEmpty()) {
            return 0L;
        }
        return orders.stream()
                .mapToLong(order -> paidAmountCents(order, paidAmountMap))
                .sum();
    }

    /**
     * 分析页收入确认口径：关闭、取消、退款中的订单不确认收入，即使历史分期表仍保留已支付记录。
     */
    public long revenueAmountCents(Order order, Map<Integer, Long> paidAmountMap) {
        return isRevenueEligible(order) ? paidAmountCents(order, paidAmountMap) : 0L;
    }

    /**
     * 汇总分析页确认收入，单位为分。
     */
    public long sumRevenueAmountCents(List<Order> orders, Map<Integer, Long> paidAmountMap) {
        if (orders == null || orders.isEmpty()) {
            return 0L;
        }
        return orders.stream()
                .mapToLong(order -> revenueAmountCents(order, paidAmountMap))
                .sum();
    }

    /**
     * 判断订单是否可进入收入、押金规模和租期贡献统计。
     */
    public boolean isRevenueEligible(Order order) {
        if (order == null) {
            return false;
        }
        String status = normalizeStatus(order.getAlipayStatus());
        return !REVENUE_EXCLUDED_STATUSES.contains(status);
    }

    private List<Integer> normalizeOrderIds(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }
        return orders.stream()
                .filter(Objects::nonNull)
                .map(Order::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private long parseYuanToCents(String yuan) {
        if (yuan == null || yuan.trim().isEmpty()) {
            return 0L;
        }
        try {
            return OrderUtil.convertYuanToCent(yuan);
        } catch (IllegalArgumentException ex) {
            log.warn("分期金额格式不正确，已按0处理: {}", yuan);
            return 0L;
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return AlipayRentConstants.STATUS_FINISHED;
        }
        return status.trim().toUpperCase();
    }
}

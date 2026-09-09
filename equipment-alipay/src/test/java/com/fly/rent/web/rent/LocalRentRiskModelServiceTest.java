package com.fly.rent.web.rent;

import com.fly.rent.config.AlipayRentConstants;
import com.fly.rent.entity.Order;
import com.fly.rent.entity.RentDepositDeductRecord;
import com.fly.rent.entity.User;
import com.fly.rent.mapper.OrderMapper;
import com.fly.rent.mapper.RentDepositDeductRecordMapper;
import com.fly.rent.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalRentRiskModelServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RentDepositDeductRecordMapper deductRecordMapper;
    @Mock
    private UserMapper userMapper;

    @Test
    void evaluateReturnsLowRiskWhenUserHasNoHistoricalOrders() {
        Order current = order(100, "ORD100", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_SIGNED, 100_00, 1_700_000_000_000L);
        when(orderMapper.selectList(any())).thenReturn(Collections.singletonList(current));

        Map<String, Object> risk = service().evaluate(current);

        assertEquals("LOW", risk.get("level"));
        assertEquals(0, risk.get("score"));
        assertTrue(String.valueOf(risk.get("summary")).contains("历史 0 单"));
        assertEquals(0, stats(risk, "orderStats").get("totalOrders"));
    }

    @Test
    void evaluateScoresSingleSuccessfulDeductHistoryAsMediumRisk() {
        Order current = order(100, "ORD100", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_SIGNED, 100_000, 1_700_000_000_000L);
        Order previous = order(90, "ORD090", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_FINISHED, 60_000, 1_690_000_000_000L);
        when(orderMapper.selectList(any())).thenReturn(Arrays.asList(current, previous));
        when(deductRecordMapper.selectList(any())).thenReturn(Collections.singletonList(
                deduct(1, 90, "ORD090", RentDepositDeductRecord.STATUS_SUCCESS, 30_000, 20)
        ));

        Map<String, Object> risk = service().evaluate(current);

        assertEquals("MEDIUM", risk.get("level"));
        assertEquals(30, risk.get("score"));
        assertEquals(1, stats(risk, "aftersaleStats").get("successRecords"));
        assertEquals(30_000, stats(risk, "aftersaleStats").get("successAmount"));
        assertRule(risk, "DEDUCT_HISTORY");
    }

    @Test
    void evaluateScoresMultipleDeductsAndLargeAmountAsHighRisk() {
        Order current = order(100, "ORD100", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_SIGNED, 100_000, 1_700_000_000_000L);
        Order previousA = order(90, "ORD090", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_FINISHED, 60_000, 1_690_000_000_000L);
        Order previousB = order(91, "ORD091", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_FINISHED, 80_000, 1_691_000_000_000L);
        when(orderMapper.selectList(any())).thenReturn(Arrays.asList(current, previousA, previousB));
        when(deductRecordMapper.selectList(any())).thenReturn(Arrays.asList(
                deduct(1, 90, "ORD090", RentDepositDeductRecord.STATUS_SUCCESS, 30_000, 20),
                deduct(2, 91, "ORD091", RentDepositDeductRecord.STATUS_SUCCESS, 40_000, 10)
        ));

        Map<String, Object> risk = service().evaluate(current);

        assertEquals("HIGH", risk.get("level"));
        assertEquals(70, risk.get("score"));
        assertEquals(70_000, stats(risk, "aftersaleStats").get("successAmount"));
        assertRule(risk, "DEDUCT_HISTORY");
        assertRule(risk, "DEDUCT_AMOUNT_RATIO");
    }

    @Test
    void evaluateScoresConcurrentActiveHistoricalOrders() {
        Order current = order(100, "ORD100", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_SIGNED, 100_000, 1_700_000_000_000L);
        Order activeA = order(90, "ORD090", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_PAID, 60_000, 1_690_000_000_000L);
        Order activeB = order(91, "ORD091", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_DELIVERED, 80_000, 1_691_000_000_000L);
        when(orderMapper.selectList(any())).thenReturn(Arrays.asList(current, activeA, activeB));
        when(deductRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> risk = service().evaluate(current);

        assertEquals("LOW", risk.get("level"));
        assertEquals(20, risk.get("score"));
        assertEquals(2, stats(risk, "orderStats").get("activeOrders"));
        assertRule(risk, "CONCURRENT_ACTIVE_ORDERS");
    }

    @Test
    void evaluateUsesUserTableFallbackWhenCurrentOrderUuidIsMissing() {
        Order current = order(100, "ORD100", null, 5, "13800138000",
                AlipayRentConstants.STATUS_SIGNED, 100_000, 1_700_000_000_000L);
        Order previous = order(90, "ORD090", "USER-FALLBACK", 5, "13800138000",
                AlipayRentConstants.STATUS_PAID, 60_000, 1_690_000_000_000L);
        User user = new User();
        user.setUserId(5);
        user.setUuid("USER-FALLBACK");
        user.setUserTel("13800138000");
        when(userMapper.selectById(5)).thenReturn(user);
        when(orderMapper.selectList(any())).thenReturn(Arrays.asList(current, previous));
        when(deductRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> risk = service().evaluate(current);

        assertEquals(1, stats(risk, "orderStats").get("activeOrders"));
        verify(userMapper).selectById(5);
    }

    @Test
    void evaluateScoresRecentProcessingOrFailedAftersaleRecords() {
        Order current = order(100, "ORD100", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_SIGNED, 100_000, 1_700_000_000_000L);
        Order previous = order(90, "ORD090", "USER-A", 1, "13800138000",
                AlipayRentConstants.STATUS_FINISHED, 60_000, 1_690_000_000_000L);
        when(orderMapper.selectList(any())).thenReturn(Arrays.asList(current, previous));
        when(deductRecordMapper.selectList(any())).thenReturn(Arrays.asList(
                deduct(1, 90, "ORD090", RentDepositDeductRecord.STATUS_PROCESSING, 30_000, 10),
                deduct(2, 90, "ORD090", RentDepositDeductRecord.STATUS_CANCELLED, 20_000, 5)
        ));

        Map<String, Object> risk = service().evaluate(current);

        assertEquals("LOW", risk.get("level"));
        assertEquals(15, risk.get("score"));
        assertEquals(1, stats(risk, "aftersaleStats").get("processingRecords"));
        assertRule(risk, "RECENT_AFTERSALE_UNRESOLVED");
    }

    private LocalRentRiskModelService service() {
        return new LocalRentRiskModelService(orderMapper, deductRecordMapper, userMapper);
    }

    private Order order(Integer id, String orderNo, String userUuid, Integer userId, String tel,
                        String status, Integer deposit, Long createdAt) {
        Order order = new Order();
        order.setOrderId(id);
        order.setOrderNo(orderNo);
        order.setUserUuid(userUuid);
        order.setUserId(userId);
        order.setUserTel(tel);
        order.setUserTitle("测试用户");
        order.setAlipayStatus(status);
        order.setOrderDeposit(deposit);
        order.setGoodTitle("无人机");
        order.setCreatetime(createdAt);
        return order;
    }

    private RentDepositDeductRecord deduct(Integer id, Integer orderId, String orderNo,
                                           String status, Integer amount, int daysAgo) {
        RentDepositDeductRecord record = new RentDepositDeductRecord();
        record.setId(id);
        record.setOrderId(orderId);
        record.setOrderNo(orderNo);
        record.setStatus(status);
        record.setDeductAmount(amount);
        record.setReasonCode("ITEM_DAMAGED");
        record.setRemark("桨叶损坏");
        record.setCreateTime(System.currentTimeMillis() - daysAgo * 86_400_000L);
        return record;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stats(Map<String, Object> risk, String key) {
        return (Map<String, Object>) risk.get(key);
    }

    @SuppressWarnings("unchecked")
    private void assertRule(Map<String, Object> risk, String code) {
        List<Map<String, Object>> rules = (List<Map<String, Object>>) risk.get("hitRules");
        assertTrue(rules.stream().anyMatch(rule -> code.equals(rule.get("code"))),
                "Expected rule " + code + " in " + rules);
    }
}

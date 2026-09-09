package com.fly.rent.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderMapperMonthlyAnalyticsSqlTest {

    @Test
    void buildsEffectiveGoodsSalesAggregationSql() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(OrderMapper.class);
        MappedStatement statement = configuration.getMappedStatement(
                OrderMapper.class.getName() + ".countEffectiveSalesByGoodIds"
        );
        Map<String, Object> params = new HashMap<>();
        params.put("goodIds", Arrays.asList(7, 8));
        params.put("includedStatuses", Arrays.asList(
                "PAID", "DELIVERED", "RECEIVED", "RETURN_DELIVERED", "RETURN_RECEIVED", "FINISHED"));

        String sql = statement.getBoundSql(params).getSql().replaceAll("\\s+", " ").trim();

        assertTrue(sql.contains("SUM(CASE WHEN o.quantity IS NULL OR o.quantity < 1 THEN 1 ELSE o.quantity END)"));
        assertTrue(sql.contains("o.goods_id IN ( ? , ? )"));
        assertTrue(sql.contains("UPPER(TRIM(o.alipay_status)) IN ( ? , ? , ? , ? , ? , ? )"));
        assertTrue(sql.endsWith("GROUP BY o.goods_id"));
    }

    @Test
    void buildsSingleUserScopedAggregateSql() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(OrderMapper.class);
        MappedStatement statement = configuration.getMappedStatement(
                OrderMapper.class.getName() + ".selectMonthlyAnalytics"
        );

        Map<String, Object> params = new HashMap<>();
        params.put("userUuid", "user-a");
        params.put("monthStartMs", 1L);
        params.put("nextMonthStartMs", 100L);
        params.put("bucket2StartMs", 8L);
        params.put("bucket3StartMs", 15L);
        params.put("bucket4StartMs", 22L);
        params.put("activeStatuses", Arrays.asList(
                "CREATED", "SIGNED", "APPROVED", "PAID", "DELIVERED",
                "RECEIVED", "RETURN_DELIVERED", "RETURN_RECEIVED"
        ));
        params.put("finishedStatus", "FINISHED");
        params.put("closedStatuses", Arrays.asList("PENDING_CANCEL", "CLOSED", "CANCELLED", "REFUNDING", "REFUNDED"));

        BoundSql boundSql = statement.getBoundSql(params);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertTrue(sql.startsWith("SELECT COUNT(*) AS orderCount"));
        assertTrue(sql.contains("FROM rent_order o WHERE o.user_uuid = ?"));
        assertTrue(sql.contains("o.created_at >= ? AND o.created_at < ?"));
        assertTrue(sql.contains("o.alipay_status IN ( ? , ? , ? , ? , ? , ? , ? , ? )"));
        assertTrue(sql.contains("EXISTS ( SELECT 1 FROM rent_order_relet_relation relation"));
        assertTrue(sql.contains("relation.child_order_id = o.order_id AND relation.user_uuid = ?"));
        assertFalse(sql.contains("o.total_amount"));
        assertFalse(sql.contains("o.order_no"));
        assertFalse(sql.contains("o.goods_title"));
        assertFalse(sql.contains("o.user_phone"));
        assertFalse(sql.contains("o.address"));
    }
}

package com.fly.rent.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderMapperYearlyAnalyticsSqlTest {
    @Test void buildsOneUserScopedMonthlyGroupedAnnualQuery() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(OrderMapper.class);
        MappedStatement statement = configuration.getMappedStatement(OrderMapper.class.getName() + ".selectYearlyAnalytics");
        Map<String, Object> params = new HashMap<>();
        params.put("userUuid", "user-a"); params.put("yearStartMs", 1L); params.put("nextYearStartMs", 2L);
        params.put("businessTimezone", "Asia/Shanghai");
        params.put("activeStatuses", Arrays.asList("CREATED", "SIGNED", "APPROVED", "PAID", "DELIVERED", "RECEIVED", "RETURN_DELIVERED", "RETURN_RECEIVED"));
        params.put("finishedStatus", "FINISHED");
        params.put("closedStatuses", Arrays.asList("PENDING_CANCEL", "CLOSED", "CANCELLED", "REFUNDING", "REFUNDED"));
        String sql = statement.getBoundSql(params).getSql().replaceAll("\\s+", " ").trim();
        assertTrue(sql.contains("o.user_uuid = ?"));
        assertTrue(sql.contains("o.created_at >= ? AND o.created_at < ?"));
        assertTrue(sql.contains("GROUP BY month ORDER BY month"));
        assertTrue(sql.contains("rent_order_relet_relation"));
        assertTrue(sql.contains("relation.user_uuid = ?"));
        assertTrue(sql.contains("CONVERT_TZ"));
        assertFalse(sql.contains("goods_title")); assertFalse(sql.contains("message"));
    }
}

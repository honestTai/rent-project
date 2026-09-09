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

class GoodMapperCatalogSqlTest {

    @Test
    void categoryCountsUseOneParameterizedGroupedDatabaseQuery() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(GoodMapper.class);
        MappedStatement statement = configuration.getMappedStatement(
                GoodMapper.class.getName() + ".countVisibleGoodsByCategoryCodes");
        Map<String, Object> params = new HashMap<>();
        params.put("categoryCodes", Arrays.asList("drone-portable", "drone-pro"));

        BoundSql boundSql = statement.getBoundSql(params);
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertTrue(sql.contains("COUNT(DISTINCT g.goods_id)"));
        assertTrue(sql.contains("g.category_code IN ( ? , ? )"));
        assertTrue(sql.contains("EXISTS (SELECT 1 FROM goods_sku s WHERE s.goods_id = g.goods_id)"));
        assertTrue(sql.endsWith("GROUP BY g.category_code"));
        assertFalse(sql.toLowerCase().contains("title like"));
    }
}

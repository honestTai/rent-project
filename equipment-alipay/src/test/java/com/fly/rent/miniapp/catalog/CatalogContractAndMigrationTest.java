package com.fly.rent.miniapp.catalog;

import com.fly.rent.entity.Result;
import com.fly.rent.support.util.ResultUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogContractAndMigrationTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void unifiedEnvelopeContainsTraceIdAndStableSuccessMessageShape() {
        MDC.put("traceId", "trace-catalog-1");

        Result result = ResultUtil.success("data");

        assertEquals(0, result.getCode());
        assertEquals("OK", result.getMsg());
        assertNull(result.getMessage());
        assertEquals("trace-catalog-1", result.getTraceId());
    }

    @Test
    void mappingMigrationIsIdempotentAndNeverGuessesCategoryFromGoodsTitle() throws Exception {
        String schema = resource("/sql/rent_catalog_categories.sql");
        String apply = resource("/sql/rent_catalog_goods_mapping_apply.sql");

        assertTrue(schema.contains("information_schema.COLUMNS"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS rent_catalog_goods_category_mapping"));
        assertTrue(apply.contains("review_status='APPROVED'"));
        assertTrue(apply.contains("INSERT IGNORE INTO rent_catalog_goods_category_backup"));
        assertTrue(apply.contains("gate_status"));
        assertFalse(apply.toLowerCase().contains("title like"));
        assertFalse(apply.toLowerCase().contains("case when g.title"));
    }

    private String resource(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(input != null, "missing test resource " + path);
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                output.write(buffer, 0, length);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}

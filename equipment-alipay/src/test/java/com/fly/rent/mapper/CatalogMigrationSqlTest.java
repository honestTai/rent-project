package com.fly.rent.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CatalogMigrationSqlTest {

    @Test
    void iconMigrationIsGuardedAndRepeatable() throws IOException {
        String sql;
        try (InputStream input = getClass().getResourceAsStream("/sql/rent_catalog_categories.sql")) {
            assertTrue(input != null, "catalog migration resource must exist");
            try (Scanner scanner = new Scanner(input, "UTF-8").useDelimiter("\\A")) {
                sql = scanner.hasNext() ? scanner.next() : "";
            }
        }

        assertTrue(sql.contains("TABLE_NAME='category' AND COLUMN_NAME='icon_name'"));
        assertTrue(sql.contains("ADD COLUMN `icon_name` varchar(64) DEFAULT NULL"));
        assertTrue(sql.contains("AND (icon_name IS NULL OR icon_name='')"));
        assertTrue(sql.contains("MODIFY COLUMN `icon_name` varchar(64) NOT NULL"));
        assertFalse(sql.contains("(code,parent_code,name,short_name,description,sort_order,status)"));
        assertTrue(sql.contains("(code,parent_code,name,short_name,description,icon_name,sort_order,status)"));
        assertTrue(sql.contains("WHEN 'drone' THEN 'CompassOutline'"));
        assertTrue(sql.contains("WHEN 'accessory-service' THEN 'CheckShieldOutline'"));
        assertTrue(sql.contains("INDEX_NAME='idx_rent_order_goods_id'"));
        assertTrue(sql.contains("CREATE INDEX `idx_rent_order_goods_id` ON `rent_order` (`goods_id`)"));
    }
}

#!/usr/bin/env python3
"""Offline contract checks. Does not connect to a database or start services."""
from pathlib import Path
import re
import unittest

import initialize as init

ROOT = Path(__file__).resolve().parents[2]


def csv_values(value):
    # Seed config/task rows are single VALUES tuples without nested functions.
    return [x.strip() for x in re.split(r",(?=(?:[^']*'[^']*')*[^']*$)", value)]


def seed_rows(table):
    rows = []
    for statement in init.split_sql((init.SQL_DIR / "seed-platform.sql").read_text(encoding="utf-8")):
        match = re.fullmatch(r"INSERT INTO `" + table + r"` \((.*?)\) VALUES \((.*?)\)(?: ON DUPLICATE KEY UPDATE .*)?", statement, re.S)
        if match:
            columns = [x.strip("`") for x in match[1].split(",")]
            values = csv_values(match[2])
            if len(columns) != len(values):
                raise ValueError("Seed row has a column/value mismatch")
            rows.append(dict(zip(columns, values)))
    return rows


def schema_columns():
    schemas = {}
    lane = None
    source = (init.SQL_DIR / "init.sql").read_text(encoding="utf-8")
    for part in re.split(r"(?m)^-- @database (platform|alipay)\s*$", source):
        if part in {"platform", "alipay"}:
            lane = part
            continue
        for statement in init.split_sql(part):
            match = re.match(r"CREATE TABLE `(\w+)`\s*\(([\s\S]*)\) ENGINE", statement)
            if not match:
                raise ValueError("Schema files must only contain CREATE TABLE statements")
            key = (lane, match[1])
            if key in schemas:
                raise ValueError("Duplicate table in schema")
            schemas[key] = set(re.findall(r"^\s*`?(\w+)`?\s+(?:varchar|char|bigint|int|tinyint|smallint|mediumint|longtext|mediumtext|text|date|datetime|timestamp|decimal|double|float|bit|json)\b", match[2], re.M | re.I))
    return schemas


class ReadOnlyCursor:
    def __init__(self, existing=(), markers=None):
        self.existing = existing
        self.markers = markers or {}
        self.result = None
        self.calls = []

    def execute(self, query, params=None):
        if not query.startswith("SELECT "):
            raise AssertionError("Preflight attempted a write")
        self.calls.append(query)
        if "SCHEMATA" in query:
            self.result = [(x,) for x in self.existing]
        elif "information_schema.TABLES" in query:
            self.result = [(int(params[0] in self.markers),)]
        else:
            name = re.search(r"FROM `(\w+)`", query)[1]
            self.result = [self.markers[name]]

    def fetchall(self):
        return self.result

    def fetchone(self):
        return self.result[0]


class InitializationChecks(unittest.TestCase):
    def test_sql_parser_keeps_quoted_semicolons_and_discards_comments(self):
        self.assertEqual(init.split_sql("-- first\nSELECT 'a;b'; /* x;y */ SELECT 'it''s';"), ["SELECT 'a;b'", "SELECT 'it''s'"])
        with self.assertRaises(ValueError):
            init.split_sql("SELECT 'unfinished")

    def test_database_identifier_validation(self):
        for bad in ("mysql", "sys", "one;DROP DATABASE other", "other`", "a", "UPPER_CASE", "9start"):
            with self.assertRaises(ValueError):
                init.database_name(bad)
        self.assertEqual(init.database_name("equipment_platform"), "equipment_platform")

    def test_new_databases_are_read_only_until_execute(self):
        cursor = ReadOnlyCursor()
        self.assertEqual(init.inspect_existing(cursor, ("ems_a", "ems_b"), "digest", False), "new")

    def test_existing_unmarked_or_partial_databases_are_refused(self):
        for existing in [("ems_a",), ("ems_a", "ems_b")]:
            with self.assertRaises(ValueError):
                init.inspect_existing(ReadOnlyCursor(existing), ("ems_a", "ems_b"), "digest", False)

    def test_repeat_is_noop_only_for_matching_completed_pair(self):
        names = ("ems_a", "ems_b")
        marker = (init.VERSION, "digest", ",".join(names), 0, 1)
        self.assertEqual(init.inspect_existing(ReadOnlyCursor(names, dict.fromkeys(names, marker)), names, "digest", False), "complete")
        for field, replacement in [(0, "old"), (1, "changed"), (2, "wrong,pair"), (3, 1), (4, 0)]:
            changed = list(marker)
            changed[field] = replacement
            with self.assertRaises(ValueError):
                init.inspect_existing(ReadOnlyCursor(names, dict.fromkeys(names, tuple(changed))), names, "digest", False)

    def test_schema_contains_all_mapped_entity_columns(self):
        schemas = schema_columns()
        self.assertEqual(sum(k[0] == "platform" for k in schemas), 12)
        self.assertEqual(sum(k[0] == "alipay" for k in schemas), 25)
        checked = 0
        for module, lane in [("equipment-platform", "platform"), ("equipment-alipay", "alipay")]:
            for path in (ROOT / module / "src/main/java").rglob("*.java"):
                text = path.read_text(encoding="utf-8")
                table = re.search(r'@TableName\(\s*(?:value\s*=\s*)?"(\w+)"\s*\)', text)
                if not table:
                    continue
                checked += 1
                self.assertIn((lane, table[1]), schemas, str(path))
                for field in re.finditer(r"((?:\s*@\w+(?:\([^;]*?\))?\s*)*)private\s+(?!static)([\w<>?, .]+)\s+(\w+)\s*;", text):
                    annotations, _, name = field.groups()
                    if re.search(r"@TableField\([^)]*exist\s*=\s*false", annotations):
                        continue
                    explicit = re.search(r'@Table(?:Field|Id)\(\s*(?:value\s*=\s*)?"(\w+)"', annotations)
                    column = explicit[1] if explicit else re.sub(r"(?<!^)([A-Z])", r"_\1", name).lower()
                    self.assertIn(column, schemas[(lane, table[1])], f"{path.name}: {column}")
        self.assertGreaterEqual(checked, 26)

    def test_every_builtin_task_is_present_and_disabled(self):
        tasks = seed_rows("platform_schedule_task")
        source = (ROOT / "equipment-platform/src/main/java/com/equipment/platform/service/PlatformScheduleTaskService.java").read_text(encoding="utf-8")
        builtin = set(re.findall(r'task\("alipay", "([^"]+)"', source))
        self.assertEqual({r["task_code"].strip("'") for r in tasks}, builtin)
        self.assertTrue(all(r["enabled"] == "0" for r in tasks))

    def test_sensitive_config_is_blank_and_external_actions_disabled(self):
        rows = seed_rows("platform_config")
        self.assertEqual(len(rows), 151)
        configs = {r["config_key"].strip("'"): r for r in rows}
        for row in rows:
            if row["secret_flag"] == "1" or row["value_type"] == "'secret'":
                self.assertEqual(row["config_value"], "''", row["config_key"])
        for key in ("withhold.scheduler.enabled", "withhold.sign-entry.enabled", "notify.feishu.enabled", "esign.enabled"):
            self.assertEqual(configs[key]["config_value"], "'false'")
        self.assertEqual(configs["rent.return.address.json"]["config_value"], "'[]'")
        for key in ("miniapp.jwt.server-secret", "my.jwt.server-secret", "rent.return.mobile", "rent.service-phone"):
            self.assertEqual(configs[key]["config_value"], "''")

    def test_rbac_seed_matches_public_repository_modules(self):
        for path in (init.SQL_DIR / "seed-platform.sql", ROOT / "equipment-platform/src/main/resources/sql/platform_rbac_alipay_agent_delta.sql"):
            source = path.read_text(encoding="utf-8")
            self.assertNotRegex(source, r"'rental'|'secondhand'")
        source = (init.SQL_DIR / "seed-platform.sql").read_text(encoding="utf-8")
        self.assertIn("'deliveryShowcase'", source)
        self.assertIn("'/alipay/delivery-showcase'", source)

    def test_seed_contains_no_credentials_orders_or_destructive_ddl(self):
        for name in init.SQL_FILES:
            source = (init.SQL_DIR / name).read_text(encoding="utf-8")
            for statement in init.split_sql(source):
                self.assertFalse(re.match(r"(?:DROP|TRUNCATE|CREATE DATABASE|USE)\b", statement, re.I), name)
                if name != "demo.sql":
                    self.assertFalse(re.match(r"INSERT\s+(?:IGNORE\s+)?INTO\s+`?(?:user|rent_order|order_contract|installment_bill|platform_user_role)`?\s", statement, re.I), name)
            urls = re.findall(r"https?://[^\s'\"]+", source)
            self.assertTrue(all(url == "https://openapi.alipay.com/gateway.do" for url in urls), name)
        demo = init.split_sql((init.SQL_DIR / "demo.sql").read_text(encoding="utf-8"))
        self.assertEqual(len(demo), 11)
        self.assertTrue(demo[0].startswith("INSERT INTO goods "))
        self.assertTrue(demo[1].startswith("INSERT INTO goods_sku "))
        self.assertIn(",0,2,'demo-camera'", demo[0])
        self.assertIn(",0,2,'demo-drone'", demo[0])
        source = (init.SQL_DIR / "demo.sql").read_text(encoding="utf-8")
        self.assertIn("DEMO-ORDER-0001", source)
        self.assertIn("DEMO-ORDER-0002", source)
        self.assertIn("DEMO-ORDER-0003", source)
        # The order list requires a local source_id; keep it visibly fictional.
        for number in range(1, 4):
            self.assertIn(f"'DEMO-ORDER-000{number}','DEMO-SOURCE-000{number}'", source)
        # No columns containing real provider identity or executable authorization are seeded.
        for statement in demo:
            match = re.match(r"INSERT INTO\s+`?\w+`?\s*\(([^)]*)\)", statement)
            if match:
                columns = {x.strip() for x in match[1].split(",")}
                self.assertTrue(columns.isdisjoint({"auth_no", "payment_trade_no", "alipay_order_id", "alipay_user_id", "openid", "flow_id", "signer_id", "file_id", "trade_no", "agreement_no"}))


if __name__ == "__main__":
    unittest.main(verbosity=2)

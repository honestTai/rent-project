#!/usr/bin/env python3
"""Initialize two NEW MySQL 8 databases; never reset or upgrade existing data."""
from __future__ import annotations

import argparse
import base64
import getpass
import hashlib
import os
from pathlib import Path
import re
import secrets
import ssl
import sys

SQL_DIR = Path(__file__).resolve().parents[1] / "sql"
VERSION = "open-source-2026-09-09-v2"
MARKER = "ems_initialization"
SQL_FILES = ("init.sql", "seed-platform.sql", "seed-alipay.sql", "demo.sql")


def split_sql(source: str) -> list[str]:
    """Split our plain SQL files without splitting quoted semicolons or comments."""
    result, chunk = [], []
    quote = None
    i = 0
    while i < len(source):
        c = source[i]
        if quote:
            chunk.append(c)
            if c == "\\" and i + 1 < len(source):
                i += 1
                chunk.append(source[i])
            elif c == quote:
                if i + 1 < len(source) and source[i + 1] == quote:
                    i += 1
                    chunk.append(source[i])
                else:
                    quote = None
        elif c in "'\"`":
            quote = c
            chunk.append(c)
        elif source.startswith("--", i) and (i + 2 == len(source) or source[i + 2].isspace()):
            end = source.find("\n", i)
            i = len(source) if end == -1 else end
            chunk.append("\n")
        elif source.startswith("/*", i):
            end = source.find("*/", i + 2)
            if end == -1:
                raise ValueError("Unterminated SQL comment")
            i = end + 1
            chunk.append(" ")
        elif c == ";":
            if "".join(chunk).strip():
                result.append("".join(chunk).strip())
            chunk = []
        else:
            chunk.append(c)
        i += 1
    if quote:
        raise ValueError("Unterminated SQL string")
    if "".join(chunk).strip():
        result.append("".join(chunk).strip())
    return result


def database_name(value: str) -> str:
    if not re.fullmatch(r"[a-z][a-z0-9_]{2,47}", value):
        raise ValueError("Database names require 3-48 lowercase letters, digits or underscores")
    if value in {"mysql", "sys", "information_schema", "performance_schema"}:
        raise ValueError("System databases are forbidden")
    return value


def seed_digest() -> str:
    digest = hashlib.sha256()
    for name in SQL_FILES:
        digest.update(name.encode())
        digest.update((SQL_DIR / name).read_text(encoding="utf-8").encode("utf-8"))
    return digest.hexdigest()


def encrypt_admin_password(password: str, seed: str) -> str:
    # Matches Aes.java: SHA-256(seed)[:16], AES/ECB/PKCS5Padding.
    # Md5.md5String currently returns the original password, NOT its MD5 hash.
    from cryptography.hazmat.primitives import padding
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
    key = hashlib.sha256(seed.encode("utf-8")).digest()[:16]
    padder = padding.PKCS7(128).padder()
    padded = padder.update(password.encode("utf-8")) + padder.finalize()
    enc = Cipher(algorithms.AES(key), modes.ECB()).encryptor()
    return base64.b64encode(enc.update(padded) + enc.finalize()).decode("ascii")


def inspect_existing(cursor, names: tuple[str, str], digest: str, demo: bool) -> str:
    cursor.execute("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN (%s, %s)", names)
    existing = {row[0] for row in cursor.fetchall()}
    if not existing:
        return "new"
    if existing != set(names):
        raise ValueError("One target database already exists. No changes made; choose two new names")
    expected = (VERSION, digest, ",".join(names), int(demo), 1)
    for name in names:
        cursor.execute("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s", (name, MARKER))
        if cursor.fetchone()[0] != 1:
            raise ValueError("Existing database has no initializer marker; refusing to touch it")
        cursor.execute(f"SELECT version, seed_sha256, database_pair, demo_enabled, complete FROM `{name}`.`{MARKER}` WHERE id=1")
        if cursor.fetchone() != expected:
            raise ValueError("Existing initialization is incomplete or has a different version/options; no reset is available")
    return "complete"


def run_file(cursor, filename: str, names: tuple[str, str], default_lane: str | None = None) -> None:
    source = (SQL_DIR / filename).read_text(encoding="utf-8")
    lane = default_lane
    # Section markers are deliberately NOT executable USE statements. Raw mysql import is unsupported.
    for part in re.split(r"(?m)^-- @database (platform|alipay)\s*$", source):
        if part in {"platform", "alipay"}:
            lane = part
            continue
        statements = split_sql(part)
        if not statements:
            continue
        if lane not in {"platform", "alipay"}:
            raise ValueError(f"Missing database section in {filename}")
        cursor.execute(f"USE `{names[0 if lane == 'platform' else 1]}`")
        for statement in statements:
            cursor.execute(statement)


def initialize(cursor, connection, names: tuple[str, str], digest: str, demo: bool, admin_user: str, password_cipher: str) -> None:
    # No IF NOT EXISTS: an external race that creates a target must fail, never be adopted.
    for name in names:
        cursor.execute(f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    run_file(cursor, "init.sql", names)
    for name in names:
        cursor.execute(f"CREATE TABLE `{name}`.`{MARKER}` (id tinyint PRIMARY KEY, version varchar(64) NOT NULL, seed_sha256 char(64) NOT NULL, database_pair varchar(100) NOT NULL, demo_enabled tinyint NOT NULL, complete tinyint NOT NULL, created_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB")
    connection.begin()
    run_file(cursor, "seed-platform.sql", names, "platform")
    run_file(cursor, "seed-alipay.sql", names, "alipay")
    if demo:
        run_file(cursor, "demo.sql", names, "alipay")
        cursor.execute(f"UPDATE `{names[0]}`.platform_config SET config_value='true' WHERE system_code='alipay' AND config_key='alipay.demo-mode.enabled' AND config_value='false' AND enabled=1")
        if cursor.rowcount != 1:
            raise ValueError("Expected exactly one disabled Alipay demo-mode template")
    cursor.execute(f"UPDATE `{names[0]}`.platform_config SET config_value=%s WHERE system_code='alipay' AND config_key='miniapp.jwt.server-secret' AND (config_value IS NULL OR config_value='')", (secrets.token_hex(32),))
    if cursor.rowcount != 1:
        raise ValueError("Expected exactly one empty miniapp JWT secret template")
    cursor.execute(f"INSERT INTO `{names[0]}`.`user` (realName,userName,userPwd,role,uuid,isNoRequest) VALUES (%s,%s,%s,0,'-1',0)", ("演示管理员" if demo else "系统管理员", admin_user, password_cipher))
    admin_id = cursor.lastrowid
    cursor.execute(f"INSERT INTO `{names[0]}`.platform_user_role (user_id,role_id) SELECT %s,id FROM `{names[0]}`.platform_role WHERE role_code='super_admin'", (admin_id,))
    if cursor.rowcount != 1:
        raise ValueError("Expected exactly one super_admin role")
    for name in names:
        cursor.execute(f"INSERT INTO `{name}`.`{MARKER}` (id,version,seed_sha256,database_pair,demo_enabled,complete) VALUES (1,%s,%s,%s,%s,1)", (VERSION, digest, ",".join(names), int(demo)))
    connection.commit()


def parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=3306)
    p.add_argument("--user", required=True, help="MySQL account allowed to create both new databases")
    p.add_argument("--platform-db", required=True)
    p.add_argument("--alipay-db", required=True)
    p.add_argument("--admin-user", default="admin")
    p.add_argument("--ssl-ca", help="CA file for verified TLS; remote hosts use system CAs by default")
    p.add_argument("--demo", action="store_true", help="Include fictional screenshot fixtures; localhost and first initialization only")
    p.add_argument("--execute", action="store_true", help="Create new databases after read-only preflight")
    p.add_argument("--confirm-new-databases", help="Exact platform_db,alipay_db pair, required with --execute")
    return p


def main(argv=None) -> int:
    args = parser().parse_args(argv)
    connection = None
    try:
        names = (database_name(args.platform_db), database_name(args.alipay_db))
        if names[0] == names[1]:
            raise ValueError("Platform and Alipay databases must be different")
        if args.demo and args.host not in {"localhost", "127.0.0.1", "::1"}:
            raise ValueError("Fictional --demo data is restricted to a local loopback database; use a separate demo instance without real provider credentials")
        if args.execute and args.confirm_new_databases != ",".join(names):
            raise ValueError("--execute requires --confirm-new-databases platform_db,alipay_db with the exact target names")
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_.-]{2,63}", args.admin_user):
            raise ValueError("Admin username requires 3-64 ASCII letters, digits, dots, underscores or hyphens")
        import pymysql
        db_password = os.getenv("EMS_INIT_DB_PASSWORD")
        if db_password is None:
            db_password = getpass.getpass("MySQL password: ")
        tls = None
        if args.ssl_ca or args.host not in {"localhost", "127.0.0.1", "::1"}:
            tls = ssl.create_default_context(cafile=args.ssl_ca)
        connection = pymysql.connect(host=args.host, port=args.port, user=args.user, password=db_password, charset="utf8mb4", connect_timeout=10, read_timeout=120, write_timeout=120, autocommit=False, ssl=tls)
        digest = seed_digest()
        with connection.cursor() as cursor:
            cursor.execute("SELECT VERSION()")
            version = cursor.fetchone()[0]
            if "MariaDB" in version or not re.match(r"(?:8|9)\.", version):
                raise ValueError("This schema requires Oracle MySQL 8 or later; MariaDB is not supported")
            state = inspect_existing(cursor, names, digest, args.demo)
            print(f"Target: {args.host}:{args.port}; databases: {','.join(names)}; demo={args.demo}; state={state}")
            if state == "complete":
                print("Already initialized with this seed version/options. No data, account or configuration was changed.")
                return 0
            if not args.execute:
                print("Read-only preflight passed. Add --execute and the exact --confirm-new-databases pair to initialize.")
                return 0
            aes_seed = os.getenv("RENTAL_PRODUCT_AES_SECRET", "")
            if len(aes_seed.strip()) < 32:
                raise ValueError("Set RENTAL_PRODUCT_AES_SECRET to the same private random seed (at least 32 characters) used by the backend")
            admin_password = os.getenv("EMS_INIT_ADMIN_PASSWORD")
            if admin_password is None:
                admin_password = getpass.getpass("New administrator password (12-128 characters, include punctuation): ")
                if admin_password != getpass.getpass("Repeat administrator password: "):
                    raise ValueError("Administrator passwords do not match")
            if not 12 <= len(admin_password) <= 128 or admin_password != admin_password.strip() or re.fullmatch(r"[A-Za-z0-9+/=]+", admin_password):
                raise ValueError("Use 12-128 characters, no leading/trailing whitespace, and punctuation other than + / =")
            cipher = encrypt_admin_password(admin_password, aes_seed)
            initialize(cursor, connection, names, digest, args.demo, args.admin_user, cipher)
            if inspect_existing(cursor, names, digest, args.demo) != "complete":
                raise ValueError("Initialization marker verification failed")
            print(f"Initialized successfully. Administrator username: {args.admin_user}. Password is not displayed or stored in project files.")
            return 0
    except (ValueError, OSError, ImportError) as exc:
        if connection:
            connection.rollback()
        print(f"Initialization stopped: {exc}", file=sys.stderr)
        return 1
    except Exception as exc:
        if connection:
            connection.rollback()
        # DB exceptions can embed SQL and credential values. Print the numeric code only.
        code = exc.args[0] if exc.args and isinstance(exc.args[0], int) else "unavailable"
        print(f"Database operation failed (code {code}); any new databases are retained for inspection. Existing databases were not reset. Do not retry with destructive SQL.", file=sys.stderr)
        return 1
    finally:
        if connection:
            connection.close()


if __name__ == "__main__":
    raise SystemExit(main())

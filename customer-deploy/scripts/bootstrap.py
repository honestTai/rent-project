#!/usr/bin/env python3
"""Container-only initialization adapter. Credentials are never printed."""
import os
from pathlib import Path
import re
import sys

import initialize


def main():
    mode = os.environ.get("DB_MODE", "managed")
    host = "127.0.0.1" if mode == "managed" else os.environ["RDS_HOST"]
    user = os.getenv("EMS_INIT_DB_USERNAME") or ("root" if mode == "managed" else os.environ["RDS_USERNAME"])
    names = (initialize.database_name(os.environ["RDS_PLATFORM_DATABASE"]),
             initialize.database_name(os.environ["RDS_ALIPAY_DATABASE"]))
    if mode == "external" and os.getenv("DB_SSL_CA"):
        from cryptography import x509
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.serialization import pkcs12
        ca_path = Path(os.environ["DB_SSL_CA"])
        certificates = x509.load_pem_x509_certificates(ca_path.read_bytes())
        if not certificates:
            raise ValueError("No certificates in MySQL CA bundle")
        # Only public CA certificates, no private key; the conventional password is not a credential.
        ca_path.with_name("mysql-truststore.p12").write_bytes(pkcs12.serialize_java_truststore(
            [pkcs12.PKCS12Certificate(cert, b"mysql-ca-" + str(i).encode()) for i, cert in enumerate(certificates)],
            serialization.BestAvailableEncryption(b"changeit")))
    args = ["--host", host, "--port", os.getenv("RDS_PORT", "3306"), "--user", user,
            "--platform-db", names[0], "--alipay-db", names[1],
            "--admin-user", os.getenv("EMS_INIT_ADMIN_USERNAME") or os.getenv("ADMIN_USER", "admin"), "--execute",
            "--confirm-new-databases", ",".join(names)]
    if os.getenv("DB_SSL_CA") and mode == "external":
        args += ["--ssl-ca", os.environ["DB_SSL_CA"]]
    if os.getenv("BOOTSTRAP_DEMO", "0") == "1":
        if mode != "managed":
            raise ValueError("Demo initialization requires the isolated managed database")
        args += ["--demo"]
    result = initialize.main(args)
    if result or mode != "managed":
        return result
    import pymysql
    app_user = os.environ["RDS_USERNAME"]
    if not re.fullmatch(r"[a-z][a-z0-9_]{2,31}", app_user) or app_user == "root":
        raise ValueError("Managed application database account is invalid")
    # MYSQL_USER creates the account without a default database. Grant only this pair.
    with pymysql.connect(host=host, user=user, password=os.environ["EMS_INIT_DB_PASSWORD"],
                         charset="utf8mb4", connect_timeout=10, autocommit=True) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT @@partial_revokes")
            literal_database_names = bool(cursor.fetchone()[0])
            for name in names:
                # With partial_revokes OFF, '_' is a wildcard even in a quoted GRANT database.
                grant_name = name if literal_database_names else name.replace("_", "\\_")
                cursor.execute(f"GRANT ALL PRIVILEGES ON `{grant_name}`.* TO %s@'%%'", (app_user,))
    print("Application account granted access to the two initialized databases.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception:
        # SQL / DB-driver exceptions may contain credentials. Do not log their text.
        print("Bootstrap failed; inspect the retained database and configuration privately.", file=sys.stderr)
        raise SystemExit(1)

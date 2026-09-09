#!/bin/sh
set -eu
set -- --host="$RDS_HOST" --port="$RDS_PORT" --user="$RDS_USERNAME" \
  --ssl-mode="$DB_SSL_MODE" --single-transaction --no-tablespaces \
  --set-gtid-purged=OFF --column-statistics=0 --hex-blob --default-character-set=utf8mb4
if [ -n "${DB_SSL_CA:-}" ]; then set -- "$@" --ssl-ca="$DB_SSL_CA"; fi
# MYSQL_PWD arrives from Compose, never as a command argument. SQL goes only to stdout.
exec mysqldump "$@" --databases "$RDS_PLATFORM_DATABASE" "$RDS_ALIPAY_DATABASE"

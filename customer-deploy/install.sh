#!/bin/sh
set -eu

cd "$(dirname "$0")"
mode="${1:-start}"
if [ "$mode" != start ] && [ "$mode" != --check ]; then
  echo "Usage: sh install.sh [--check]"
  exit 1
fi
if [ ! -f .env ]; then
  cp .env.example .env
  chmod 600 .env
  echo "已创建 .env 模板。请填写独立数据库、缓存和应用密钥，再运行安装检查。"
  exit 1
fi
chmod 600 .env
if grep -Eq '^(RDS_HOST|RDS_USERNAME|RDS_PASSWORD|REDIS_PASSWORD)=($|change_this_|mysql\.example\.com)' .env; then
  echo "请先替换 .env 中数据库连接和 Redis 密码的空值或示例占位符。"
  exit 1
fi
for service in equipment-eureka equipment-platform equipment-alipay equipment-gateway; do
  if [ ! -s "jars/$service.jar" ]; then
    echo "缺少构建产物：jars/$service.jar"
    exit 1
  fi
done
for app in platform rent; do
  if [ ! -f "www/$app/index.html" ]; then
    echo "缺少前端构建产物：www/$app/index.html"
    exit 1
  fi
done
if ! command -v docker >/dev/null 2>&1 || ! docker compose version >/dev/null 2>&1; then
  echo "请先按 Docker 官方文档安装 Docker Engine 和 Compose 插件。"
  exit 1
fi
docker info >/dev/null
# --quiet avoids printing the interpolated environment and secrets.
docker compose config --quiet
if [ "$mode" = --check ]; then
  echo "部署文件与 Compose 配置检查通过；未启动服务，也未写入数据库。"
  exit 0
fi
mkdir -p data/redis uploads logs/nginx logs/equipment-eureka logs/equipment-platform logs/equipment-alipay logs/equipment-gateway
echo "启动服务。此脚本不执行数据库初始化；新库须先使用 scripts/initialize.py 初始化。"
docker compose pull
docker compose up -d
docker compose ps

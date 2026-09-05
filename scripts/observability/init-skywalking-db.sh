#!/bin/sh
# 在已有 PostgreSQL volume 中幂等创建 SkyWalking 独立数据库。
set -eu

until pg_isready -h "$PGHOST" -p "${PGPORT:-5432}" -U "$PGUSER" -d "$PGDATABASE" >/dev/null 2>&1; do
  sleep 2
done

if [ "$(psql -tAc "SELECT 1 FROM pg_database WHERE datname = 'skywalking'")" != "1" ]; then
  createdb skywalking
  echo "created database skywalking"
else
  echo "database skywalking already exists"
fi

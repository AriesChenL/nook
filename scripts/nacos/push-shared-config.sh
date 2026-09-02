#!/usr/bin/env bash
# 把 docs/nacos/nook-shared.yml 发布到 Nacos（dataId=nook-shared.yml, group=DEFAULT_GROUP, type=yaml）。
#
# 用法:
#   scripts/nacos/push-shared-config.sh                      # 本地默认 http://localhost:8848，无鉴权
#   NACOS_ADDR=http://nacos.prod:8848 NACOS_TOKEN=xxx scripts/nacos/push-shared-config.sh
#
# 幂等：重复执行即覆盖发布。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FILE="$REPO_ROOT/docs/nacos/nook-shared.yml"
NACOS_ADDR="${NACOS_ADDR:-http://localhost:8848}"
DATA_ID="nook-shared.yml"
GROUP="DEFAULT_GROUP"

[ -f "$FILE" ] || { echo "找不到 $FILE" >&2; exit 1; }

args=(--fail --silent --show-error -X POST "$NACOS_ADDR/nacos/v1/cs/configs"
  --data-urlencode "dataId=$DATA_ID"
  --data-urlencode "group=$GROUP"
  --data-urlencode "type=yaml"
  --data-urlencode "content@$FILE")
[ -n "${NACOS_TOKEN:-}" ] && args+=(--data-urlencode "accessToken=$NACOS_TOKEN")

echo "发布 $DATA_ID@$GROUP → $NACOS_ADDR ..."
if [ "$(curl "${args[@]}")" = "true" ]; then
  echo "OK"
else
  echo "发布失败（Nacos 未就绪 / 鉴权？）" >&2
  exit 1
fi

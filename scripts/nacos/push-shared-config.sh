#!/usr/bin/env bash
# 把 docs/nacos/nook-shared.yml 通过 Nacos 3 Admin API 发布到配置中心。
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

args=(--fail --silent --show-error -X POST "$NACOS_ADDR/nacos/v3/admin/cs/config"
  --data-urlencode "dataId=$DATA_ID"
  --data-urlencode "groupName=$GROUP"
  --data-urlencode "namespaceId=public"
  --data-urlencode "type=yaml"
  --data-urlencode "content@$FILE")
[ -n "${NACOS_TOKEN:-}" ] && args+=(-H "accessToken: $NACOS_TOKEN")

echo "发布 $DATA_ID@$GROUP → $NACOS_ADDR ..."
response="$(curl "${args[@]}")"
compact_response="$(printf '%s' "$response" | tr -d '[:space:]')"
if [[ "$compact_response" == *'"code":0'* && "$compact_response" == *'"data":true'* ]]; then
  echo "OK"
else
  echo "发布失败（Nacos 未就绪 / Admin API 鉴权？）：$response" >&2
  exit 1
fi

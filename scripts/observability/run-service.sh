#!/usr/bin/env bash
# 用 OpenTelemetry Java Agent 启动一个 Nook 服务，并把 trace 发送到 SkyWalking OAP。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODULE="${1:-}"
if [ -z "$MODULE" ]; then
  echo "用法: $0 <nook-gateway|nook-auth|nook-user|nook-im|nook-ai|nook-pay> [Maven 参数...]" >&2
  exit 2
fi
shift

case "$MODULE" in
  nook-gateway|nook-auth|nook-user|nook-im|nook-ai|nook-pay) ;;
  *)
    echo "不支持的服务模块: $MODULE" >&2
    exit 2
    ;;
esac

if [ -n "${OTEL_JAVAAGENT_PATH:-}" ]; then
  AGENT_PATH="$OTEL_JAVAAGENT_PATH"
else
  AGENT_PATH="$($REPO_ROOT/scripts/observability/setup-otel-agent.sh)"
fi
[ -f "$AGENT_PATH" ] || { echo "找不到 OpenTelemetry Agent: $AGENT_PATH" >&2; exit 1; }

export OTEL_SERVICE_NAME="${OTEL_SERVICE_NAME:-$MODULE}"
export OTEL_EXPORTER_OTLP_ENDPOINT="${OTEL_EXPORTER_OTLP_ENDPOINT:-http://127.0.0.1:11800}"
export OTEL_EXPORTER_OTLP_PROTOCOL="${OTEL_EXPORTER_OTLP_PROTOCOL:-grpc}"
export OTEL_TRACES_EXPORTER="${OTEL_TRACES_EXPORTER:-otlp}"
export OTEL_METRICS_EXPORTER="${OTEL_METRICS_EXPORTER:-none}"
export OTEL_LOGS_EXPORTER="${OTEL_LOGS_EXPORTER:-none}"
export OTEL_PROPAGATORS="${OTEL_PROPAGATORS:-tracecontext,baggage}"
export OTEL_EXPORTER_OTLP_COMPRESSION="${OTEL_EXPORTER_OTLP_COMPRESSION:-gzip}"
export OTEL_EXPORTER_OTLP_TIMEOUT="${OTEL_EXPORTER_OTLP_TIMEOUT:-10000}"
export OTEL_JAVAAGENT_LOGGING="${OTEL_JAVAAGENT_LOGGING:-simple}"
export OTEL_RESOURCE_ATTRIBUTES="${OTEL_RESOURCE_ATTRIBUTES:-service.namespace=nook,deployment.environment.name=local,service.instance.id=$MODULE-local}"

echo "启动 ${MODULE}，trace -> $OTEL_EXPORTER_OTLP_ENDPOINT" >&2
"$REPO_ROOT/mvnw" -pl "$MODULE" -am install -DskipTests
exec "$REPO_ROOT/mvnw" -pl "$MODULE" spring-boot:run \
  "-Dspring-boot.run.jvmArguments=-javaagent:$AGENT_PATH" "$@"

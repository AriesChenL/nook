#!/usr/bin/env bash
# 下载并校验 Nook 统一使用的 OpenTelemetry Java Agent。
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
AGENT_VERSION="2.31.1"
AGENT_SHA256="bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba"
RUNTIME_ROOT="${NOOK_RUNTIME_DIR:-$REPO_ROOT/.runtime}"
AGENT_DIR="$RUNTIME_ROOT/opentelemetry"
AGENT_PATH="$AGENT_DIR/opentelemetry-javaagent-$AGENT_VERSION.jar"
DOWNLOAD_URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v$AGENT_VERSION/opentelemetry-javaagent.jar"

checksum() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    echo "需要 shasum 或 sha256sum 来校验 Agent" >&2
    exit 1
  fi
}

if [ -f "$AGENT_PATH" ]; then
  if [ "$(checksum "$AGENT_PATH")" = "$AGENT_SHA256" ]; then
    echo "$AGENT_PATH"
    exit 0
  fi
  echo "已存在的 Agent 校验失败：$AGENT_PATH" >&2
  exit 1
fi

mkdir -p "$AGENT_DIR"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT
TEMP_JAR="$TEMP_DIR/opentelemetry-javaagent.jar"

echo "下载 OpenTelemetry Java Agent $AGENT_VERSION ..." >&2
curl --fail --location --silent --show-error "$DOWNLOAD_URL" --output "$TEMP_JAR"
ACTUAL_SHA256="$(checksum "$TEMP_JAR")"
if [ "$ACTUAL_SHA256" != "$AGENT_SHA256" ]; then
  echo "Agent SHA-256 校验失败，expected=$AGENT_SHA256 actual=$ACTUAL_SHA256" >&2
  exit 1
fi

install -m 0644 "$TEMP_JAR" "$AGENT_PATH"
echo "$AGENT_PATH"

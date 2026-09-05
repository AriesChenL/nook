param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("nook-gateway", "nook-auth", "nook-user", "nook-im", "nook-ai", "nook-pay")]
    [string]$Module,

    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$AgentPath = if ($env:OTEL_JAVAAGENT_PATH) {
    $env:OTEL_JAVAAGENT_PATH
} else {
    & (Join-Path $PSScriptRoot "setup-otel-agent.ps1")
}

if (-not (Test-Path $AgentPath)) { throw "找不到 OpenTelemetry Agent: $AgentPath" }
if (-not $env:OTEL_SERVICE_NAME) { $env:OTEL_SERVICE_NAME = $Module }
if (-not $env:OTEL_EXPORTER_OTLP_ENDPOINT) { $env:OTEL_EXPORTER_OTLP_ENDPOINT = "http://127.0.0.1:11800" }
if (-not $env:OTEL_EXPORTER_OTLP_PROTOCOL) { $env:OTEL_EXPORTER_OTLP_PROTOCOL = "grpc" }
if (-not $env:OTEL_TRACES_EXPORTER) { $env:OTEL_TRACES_EXPORTER = "otlp" }
if (-not $env:OTEL_METRICS_EXPORTER) { $env:OTEL_METRICS_EXPORTER = "none" }
if (-not $env:OTEL_LOGS_EXPORTER) { $env:OTEL_LOGS_EXPORTER = "none" }
if (-not $env:OTEL_PROPAGATORS) { $env:OTEL_PROPAGATORS = "tracecontext,baggage" }
if (-not $env:OTEL_EXPORTER_OTLP_COMPRESSION) { $env:OTEL_EXPORTER_OTLP_COMPRESSION = "gzip" }
if (-not $env:OTEL_EXPORTER_OTLP_TIMEOUT) { $env:OTEL_EXPORTER_OTLP_TIMEOUT = "10000" }
if (-not $env:OTEL_JAVAAGENT_LOGGING) { $env:OTEL_JAVAAGENT_LOGGING = "simple" }
if (-not $env:OTEL_RESOURCE_ATTRIBUTES) {
    $env:OTEL_RESOURCE_ATTRIBUTES = "service.namespace=nook,deployment.environment.name=local,service.instance.id=$Module-local"
}

Write-Host "启动 $Module，trace -> $env:OTEL_EXPORTER_OTLP_ENDPOINT" -ForegroundColor Cyan
$MavenWrapper = Join-Path $RepoRoot "mvnw.cmd"
& $MavenWrapper -pl $Module -am install -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$JvmArguments = "-Dspring-boot.run.jvmArguments=-javaagent:$AgentPath"
& $MavenWrapper -pl $Module spring-boot:run $JvmArguments @MavenArgs
exit $LASTEXITCODE

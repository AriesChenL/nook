param()

$ErrorActionPreference = "Stop"
$AgentVersion = "2.31.1"
$AgentSha256 = "BBF83C151B6400709E2F225BDD07A04F839D9D13B8B93464241333FD25D3E3BA"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$RuntimeRoot = if ($env:NOOK_RUNTIME_DIR) { $env:NOOK_RUNTIME_DIR } else { Join-Path $RepoRoot ".runtime" }
$AgentDir = Join-Path $RuntimeRoot "opentelemetry"
$AgentPath = Join-Path $AgentDir "opentelemetry-javaagent-$AgentVersion.jar"
$DownloadUrl = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v$AgentVersion/opentelemetry-javaagent.jar"

if (Test-Path $AgentPath) {
    $ActualSha256 = (Get-FileHash -Algorithm SHA256 $AgentPath).Hash
    if ($ActualSha256 -ne $AgentSha256) {
        throw "Agent SHA-256 校验失败: $AgentPath"
    }
    Write-Output $AgentPath
    exit 0
}

New-Item -ItemType Directory -Force -Path $AgentDir | Out-Null
$TempPath = Join-Path ([System.IO.Path]::GetTempPath()) "nook-opentelemetry-javaagent-$PID.jar"
try {
    Write-Host "下载 OpenTelemetry Java Agent $AgentVersion ..." -ForegroundColor Cyan
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $TempPath -UseBasicParsing
    $ActualSha256 = (Get-FileHash -Algorithm SHA256 $TempPath).Hash
    if ($ActualSha256 -ne $AgentSha256) {
        throw "Agent SHA-256 校验失败，expected=$AgentSha256 actual=$ActualSha256"
    }
    Move-Item -Force $TempPath $AgentPath
} finally {
    if (Test-Path $TempPath) { Remove-Item -Force $TempPath }
}

Write-Output $AgentPath

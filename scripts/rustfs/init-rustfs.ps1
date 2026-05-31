# 初始化 RustFS：创建 bucket nook-im、设公开读策略、配置 CORS（允许前端 5173 直传）。
# 前置：1) docker compose up -d rustfs 已启动；2) 本机已安装 aws cli。
# 用法：在本目录执行  ./init-rustfs.ps1

$ErrorActionPreference = "Stop"
$env:AWS_ACCESS_KEY_ID = "rustfsadmin"
$env:AWS_SECRET_ACCESS_KEY = "rustfssecret"
$env:AWS_DEFAULT_REGION = "us-east-1"

$endpoint = "http://localhost:9000"
$bucket = "nook-im"
$here = $PSScriptRoot

Write-Host "==> 创建 bucket $bucket"
try { aws --endpoint-url $endpoint s3api create-bucket --bucket $bucket } catch { Write-Host "bucket 可能已存在，忽略" }

Write-Host "==> 设置公开读策略"
aws --endpoint-url $endpoint s3api put-bucket-policy --bucket $bucket --policy "file://$here/policy-public-read.json"

Write-Host "==> 配置 CORS"
aws --endpoint-url $endpoint s3api put-bucket-cors --bucket $bucket --cors-configuration "file://$here/cors.json"

Write-Host "==> 完成。控制台: http://localhost:9001  (rustfsadmin / rustfssecret)"

# Nook 本地可观测性

Nook 使用 OpenTelemetry Java Agent 自动采集 Java 服务 trace，并通过 OTLP/gRPC 上报到 Apache SkyWalking OAP。SkyWalking 使用独立 PostgreSQL 数据库持久化，UI 展示服务、实例、拓扑、端点和链路。

```text
nook-* JVM
  + OpenTelemetry Java Agent 2.31.1
          |
          | OTLP/gRPC :11800
          v
SkyWalking OAP 10.4.0 ----> PostgreSQL / skywalking
  OTLP trace -> Zipkin trace model/query :9412
          |
          | GraphQL/HTTP :12800
          v
SkyWalking UI :8088
```

## 为什么使用 OpenTelemetry Agent

Nook 当前使用 Spring Framework 7 和 Spring Cloud Gateway 5。SkyWalking Java Agent 9.6 的 Spring Gateway 插件只明确覆盖 Gateway 2.x 到 4.x；OpenTelemetry Java Agent 已支持 Spring Framework 7，并能直接向 SkyWalking OAP 的 OTLP receiver 上报。

因此本项目约定：

- Java 服务只挂 OpenTelemetry Java Agent。
- SkyWalking 只承担 OAP、存储和 UI。
- 不同时挂 OpenTelemetry 与 SkyWalking 两个 Java Agent。
- 仓库不提交 agent JAR 或完整解压目录；脚本下载固定版本并校验 SHA-256。

现有根目录 `opentelemetry-javaagent.jar` 和 `skywalking-agent/` 是此前手工下载的运行资产，已加入 `.gitignore`，新启动链不再依赖它们。

## 启动

macOS / Linux：

```bash
docker compose --profile observability up -d
scripts/observability/setup-otel-agent.sh

# 每个服务一个终端，按正常顺序启动
scripts/observability/run-service.sh nook-auth
scripts/observability/run-service.sh nook-gateway
scripts/observability/run-service.sh nook-user
scripts/observability/run-service.sh nook-im
scripts/observability/run-service.sh nook-ai
scripts/observability/run-service.sh nook-pay
```

Windows PowerShell：

```powershell
nook.bat observe

scripts\observability\run-service.ps1 nook-auth
scripts\observability\run-service.ps1 nook-gateway
scripts\observability\run-service.ps1 nook-user
scripts\observability\run-service.ps1 nook-im
scripts\observability\run-service.ps1 nook-ai
scripts\observability\run-service.ps1 nook-pay
```

入口：

- SkyWalking Booster UI（原生 SkyWalking Agent 数据）：<http://localhost:8088>
- OpenTelemetry Trace（Zipkin Lens）：<http://localhost:8088/zipkin/>
- OAP HTTP / GraphQL：<http://localhost:12800>
- OAP OTLP/gRPC：`localhost:11800`

Nook 使用 OpenTelemetry Agent，上报的 trace 由 OAP 转换为 Zipkin 数据模型。因此查看
Nook 服务与链路时应进入 `/zipkin/`，再按 `serviceName`（例如 `nook-gateway`）查询。
Booster UI 的“常规服务”仪表盘只查询原生 SkyWalking 数据模型，显示 `No Data` 不代表
OTLP trace 没有进入 OAP。

## 启动脚本行为

`setup-otel-agent` 下载官方 `opentelemetry-javaagent.jar` 2.31.1 到 `.runtime/opentelemetry/`，并校验固定 SHA-256。重复执行会复用校验通过的文件。

`run-service` 先把目标模块及其 reactor 依赖安装到本地 Maven 仓库，再启动目标服务，避免独立 Maven 进程命中旧版 `nook-starter`。它只把 `-javaagent` 传给 Spring Boot 应用 JVM，不会把 Maven 自身错误地注册为 Nook 服务。默认环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `OTEL_SERVICE_NAME` | 当前模块名 | SkyWalking 中的服务名 |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://127.0.0.1:11800` | OAP gRPC receiver |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` | OTel 2.x 默认是 HTTP，必须显式改为 gRPC |
| `OTEL_TRACES_EXPORTER` | `otlp` | 上报 trace |
| `OTEL_METRICS_EXPORTER` | `none` | 避免把通用 OTel metrics 当成已配置的 SkyWalking MAL 指标 |
| `OTEL_LOGS_EXPORTER` | `none` | 应用日志仍走 SLF4J；暂不重复上报完整日志 |
| `OTEL_RESOURCE_ATTRIBUTES` | namespace/environment/instance | 服务分组和实例标识 |
| `OTEL_JAVAAGENT_PATH` | 脚本下载路径 | 需要使用自定义 agent 时覆盖 |

多实例启动时必须覆盖唯一的 `service.instance.id`：

```bash
OTEL_RESOURCE_ATTRIBUTES="service.namespace=nook,deployment.environment.name=local,service.instance.id=nook-auth-local-2" \
  scripts/observability/run-service.sh nook-auth
```

## 数据库初始化

`skywalking-db-init` 是一次性 Compose 服务。它等待 PostgreSQL 健康后幂等创建 `skywalking` 数据库，再允许 OAP 启动。这样既支持全新 volume，也支持已经存在、不会重新执行 `docker-entrypoint-initdb.d` 的 volume。

OAP 自动维护 `skywalking` 数据库内的自身表；Nook 业务表仍由 Flyway 管理在 `nook` 数据库中，两者不混用。

## 验证

```bash
docker compose --profile observability ps
curl --fail http://localhost:12800/healthcheck
curl --fail http://localhost:8088/
```

随后启动至少一个带 agent 的服务并访问一次接口，例如：

```bash
curl --fail http://localhost:8081/actuator/health
```

在 Zipkin Lens 中按 `serviceName=nook-auth` 查询，应看到 `/actuator/health` trace。跨服务验证应通过网关访问一个会触发 Feign/HTTP 下游调用的接口，并确认 trace context 连成一条链路。

## 故障排查

- UI 能打开但没有 trace：先确认打开的是 `/zipkin/` 而不是 Booster UI 的“常规服务”仪表盘；再确认 Java 服务是通过 `run-service` 启动，检查 `OTEL_EXPORTER_OTLP_ENDPOINT`，并确认 OAP 的 `receiver-zipkin` / `query-zipkin` 已启用。
- Agent 报 404/协议错误：确认 `OTEL_EXPORTER_OTLP_PROTOCOL=grpc` 且端口是 `11800`，不要把 OTel 2.x 默认 HTTP 协议直接发到 gRPC 端口。
- OAP 重启循环：检查 `skywalking-db-init` 日志、`skywalking` 数据库是否存在，以及 OAP JDBC 配置。
- 服务名全是默认值：不要直接执行裸 `./mvnw spring-boot:run`；使用 `run-service` 或显式设置 `OTEL_SERVICE_NAME`。
- 启动时出现两套字节码增强错误：检查 JVM 参数，OpenTelemetry 和 SkyWalking native agent 只能保留一个。

## 边界

- 当前本地方案固定使用 OAP/UI 10.4.0，以完成已有 Booster UI 接入。升级到 OAP 11 时必须同时迁移到独立发布的 Horizon UI、增加 admin 端口和认证配置，不能只改镜像 tag。
- 本地 Compose 中的数据库口令仅供开发。生产环境使用独立数据库、Secret 管理、TLS、访问控制和容量/保留期规划。
- 当前完成的是 trace、拓扑和 trace 派生指标。通用 OTel metrics、集中日志和告警需要单独定义 SkyWalking MAL/LAL 与通知渠道后再启用。

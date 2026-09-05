# 快速开始（Quick Start）

从零把 Nook 跑起来：基础设施 → 后端 5 个服务 → 前端，约 10 分钟。

> 更完整的架构 / API / 配置说明见 [README.md](README.md)。

---

## 1. 前置依赖

| 工具 | 版本 | 说明 |
|---|---|---|
| **JDK** | **25** | `JAVA_HOME` 必须指向 JDK 25（机器默认可能是 21，需显式覆盖） |
| **Docker** | 24+ | 起 PostgreSQL / Redis / Nacos / RabbitMQ / RustFS |
| **Node.js** | 20+ | 前端 |
| **pnpm** | 9+ | 前端包管理（`npm i -g pnpm`） |

检查版本：

```bash
java -version   # 应为 25.x
docker -v
node -v && pnpm -v
```

---

## 2. 克隆

```bash
git clone https://github.com/AriesChenL/nook.git
cd nook
```

---

## 3. 起基础设施（Docker）

```bash
docker compose up -d
docker compose ps        # 看 STATUS 都是 Up / healthy
```

数据库表结构由各服务启动时 **Flyway 自动迁移**，无需手动建表。

> Windows 也可用脚本：`nook.bat up`（封装了 `docker compose up` + 下一步的 Nacos 配置推送）。

### 可选：启动 SkyWalking 链路追踪

普通 `docker compose up -d` 不启动观测组件。需要链路追踪时：

```bash
docker compose --profile observability up -d
scripts/observability/setup-otel-agent.sh
```

之后用 `scripts/observability/run-service.sh nook-auth` 等命令启动 Java 服务。Windows 使用 `nook.bat observe` 和对应 PowerShell 脚本。完整说明见 [`docs/observability.md`](docs/observability.md)。

---

## 3.5. 推送 Nacos 共享配置（必做）

DB / Redis / RabbitMQ 口令、JWT 密钥、RustFS / DeepSeek / Stripe 凭据统一放在 **Nacos 共享配置 `nook-shared.yml`**，各服务启动时加载。**没有它服务起不来。**

```bash
scripts/nacos/push-shared-config.sh      # 默认 http://localhost:8848，通过 Nacos 3 Admin API 发布
```

内容即 [`docs/nacos/nook-shared.yml`](docs/nacos/nook-shared.yml)，每项都是 `${环境变量:开发默认值}`——本地不设任何环境变量即用默认值，可直接跑。改了配置重新执行脚本即可（`refresh: true` 会热更新）。

> `nook.bat up` 会自动推一次；也可在 Nacos 控制台（http://localhost:8849）手动新建 dataId=`nook-shared.yml`、group=`DEFAULT_GROUP`、格式 YAML。

---

## 4. 配置 AI 密钥 / Stripe 密钥（支付时必需）

`nook-shared.yml` 里 `nook.ai.deepseek.api-key` 与 `nook.stripe.*` 默认为空。三种填法任选：

1. **改 Nacos 里的 `nook-shared.yml`**：把 `${DEEPSEEK_API_KEY:}` 换成真实 key，重推脚本。
2. **环境变量**：`export DEEPSEEK_API_KEY=sk-xxx`（`nook.bat` 用 `set`），再起服务。
3. **`.env` 文件**（仅 nook-ai）：`cp nook-ai/.env.example nook-ai/.env` 后填 `DEEPSEEK_API_KEY=sk-xxx`。

> 不配也能启动；AI 对话会报模型错误，Stripe 相关接口返回「支付未配置」。
> nook-pay 也支持 `--spring.profiles.active=local` 加载 `nook-pay/application-local.yml`（已 gitignore）。
> Stripe 建议使用只开放 Customer、Checkout Session、Subscription 与 Billing Portal 所需权限的 `rk_` restricted key。Webhook 还需单独配置 `whsec_` 签名密钥。

---

## 5. 起后端（6 个服务）

确保 JDK 25：

```bash
export JAVA_HOME=/path/to/jdk-25       # macOS / Linux
# set JAVA_HOME=D:\Java\jdk-25         # Windows
```

先安装一次当前 reactor 制品（后续每个服务由独立 Maven 进程启动，必须能从本地仓库解析到同版本的 `nook-common` / `nook-starter`）：

```bash
./mvnw -DskipTests install             # Windows: mvnw.cmd
```

按**固定顺序**启动（`nook-auth` 先注册到 Nacos，其余依赖它）：

```bash
./mvnw -pl nook-auth    spring-boot:run
./mvnw -pl nook-gateway spring-boot:run
./mvnw -pl nook-user    spring-boot:run
./mvnw -pl nook-im      spring-boot:run
./mvnw -pl nook-ai      spring-boot:run
./mvnw -pl nook-pay     spring-boot:run
```

需要把 trace 上报到 SkyWalking 时，用下面的脚本替代上述裸 Maven 命令：

```bash
scripts/observability/run-service.sh nook-auth
scripts/observability/run-service.sh nook-gateway
scripts/observability/run-service.sh nook-user
scripts/observability/run-service.sh nook-im
scripts/observability/run-service.sh nook-ai
scripts/observability/run-service.sh nook-pay
```

> 每个服务一个终端；或在 IDE 里分别运行各模块的 `*Application`。
> 全部 `Started ...Application` 即启动成功。

---

## 6. 起前端

```bash
cd nook-web
pnpm install
pnpm dev
```

打开 **http://localhost:5173**。

> 无后端想看 UI：在 `nook-web/.env.local` 写 `VITE_USE_MOCK=true` 走 mock。

---

## 7. 访问入口

| 入口 | 地址 | 说明 |
|---|---|---|
| **前端** | http://localhost:5173 | 唯一用户入口 |
| 网关 | http://localhost:8080 | 前端所有请求经此鉴权转发 |

后端各服务端口：auth `8081` / user `8082` / im `8083`（WebSocket）/ ai `8084` / pay `8085`。

运维控制台（开发用）：

| 控制台 | 地址 | 账号 |
|---|---|---|
| RabbitMQ | http://localhost:15672 | `nook` / `nook123` |
| RustFS（对象存储） | http://localhost:9001/rustfs/console | `rustfsadmin` / `rustfssecret` |
| Nacos | http://localhost:8849 | 已关鉴权 |
| SkyWalking | http://localhost:8088 | 使用 `observability` profile 时可用 |

---

## 8. 第一次使用

1. 打开前端 → **注册**两个账号（用于互加好友、单聊）
2. 一个账号搜索另一个 → **加好友** → 对方接受
3. 进会话 **发消息** / 发图片文件，体验实时收发、撤回、在线状态
4. 进 **AI** 页 → **新建 Agent**（填人格 persona）→ 对话，回复会**流式逐字输出**

---

## 9. 常见问题

- **启动报 JDK 版本错** → `java -version` 不是 25，重设 `JAVA_HOME`。
- **AI 对话报错** → 没配 `nook-ai/.env` 的 `DEEPSEEK_API_KEY`，或 key 无效/无余额。
- **端口被占用** → 8080-8084 / 5173 / 5432 / 6379 / 8848 / 5672 / 15672 / 9000 / 9001 需空闲。
- **服务启动即报 DB/连接失败** → 多半是没推 Nacos 共享配置。先 `scripts/nacos/push-shared-config.sh`（见 3.5），再起服务。
- **服务连不上 Nacos** → 确认 `docker compose ps` 里 `nook-nacos` 是 Up；Nacos 首启较慢（~30s）。
- **登录后请求全 401** → `nook-shared.yml` 里 `nook.jwt.secret` 变过但只推了一半，或某服务起在配置推送之前。重推脚本 + 重启 auth/gateway。
- **AI 报模型错误 / Stripe 报未配置** → `nook-shared.yml` 里对应 key 为空，按第 4 步填。
- **Stripe 回跳后仍显示 Free** → 确认 success URL 保留 `{CHECKOUT_SESSION_ID}`，并检查 `/pay/subscription/sync` 与 Webhook 日志。
- **Stripe Webhook 一直重试** → 核对 endpoint API 版本与项目 Stripe SDK、签名密钥，以及 [Stripe 上线清单](docs/stripe/go-live.md) 中要求的事件列表。
- **多实例消息广播** → 默认 `nook.im.mq.enabled=true` 走 RabbitMQ；单机不想起 broker 可在 `nook-im/application.yml` 设 `false` 走进程内直推。

---

## 10. 停止 / 清理

```bash
docker compose down        # 停容器（保留数据卷）
docker compose down -v     # 连数据一起删（彻底重来）
```

后端 / 前端各自 `Ctrl+C` 即可。

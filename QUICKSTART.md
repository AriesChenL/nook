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
```

这会拉起 5 个容器（首次拉镜像稍慢）。等它们就绪：

```bash
docker compose ps        # 看 STATUS 都是 Up / healthy
```

数据库表结构由各服务启动时 **Flyway 自动迁移**，无需手动建表。

> Windows 也可用脚本：`nook.bat up`（封装了 `docker compose up`）。

---

## 4. 配置 AI 密钥（仅 nook-ai 需要）

```bash
cp nook-ai/.env.example nook-ai/.env
```

编辑 `nook-ai/.env`，填入 DeepSeek API Key（[申请地址](https://platform.deepseek.com/api_keys)）：

```
DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxx
```

> `.env` 已 gitignore，启动时经 `spring.config.import` 自动加载。不配也能启动，但 AI 对话会报模型错误。

---

## 5. 起后端（5 个服务）

确保 JDK 25：

```bash
export JAVA_HOME=/path/to/jdk-25       # macOS / Linux
# set JAVA_HOME=D:\Java\jdk-25         # Windows
```

先编译一次（跳过测试更快）：

```bash
./mvnw -DskipTests package             # Windows: mvnw.cmd
```

按**固定顺序**启动（`nook-auth` 先注册到 Nacos，其余依赖它）：

```bash
./mvnw -pl nook-auth    spring-boot:run
./mvnw -pl nook-gateway spring-boot:run
./mvnw -pl nook-user    spring-boot:run
./mvnw -pl nook-im      spring-boot:run
./mvnw -pl nook-ai      spring-boot:run
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

后端各服务端口：auth `8081` / user `8082` / im `8083`（WebSocket）/ ai `8084`。

运维控制台（开发用）：

| 控制台 | 地址 | 账号 |
|---|---|---|
| RabbitMQ | http://localhost:15672 | `nook` / `nook123` |
| RustFS（对象存储） | http://localhost:9001/rustfs/console | `rustfsadmin` / `rustfssecret` |
| Nacos | http://localhost:8849 | 已关鉴权 |

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
- **服务连不上 Nacos/DB** → 确认 `docker compose ps` 容器都 Up；nook-auth 要先启动。
- **多实例消息广播** → 默认 `nook.im.mq.enabled=true` 走 RabbitMQ；单机不想起 broker 可在 `nook-im/application.yml` 设 `false` 走进程内直推。

---

## 10. 停止 / 清理

```bash
docker compose down        # 停容器（保留数据卷）
docker compose down -v     # 连数据一起删（彻底重来）
```

后端 / 前端各自 `Ctrl+C` 即可。

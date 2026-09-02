# Nook

> 即时通讯（单聊 / 群聊）+ AI 助手 的全栈微服务平台。后端 Spring Boot 微服务，前端 Vue 3 SPA，所有请求统一经网关鉴权后转发。

**状态**：IM 全功能可用（单聊 + 群聊 + 实时推送 + 在线状态 + 文件/图片消息）+ `nook-ai` 用户私有 AI Agent（共享长期记忆 + 流式对话）已落地，127 个单测全绿，已真实环境端到端验证。

> 🚀 想直接跑起来？看 **[QUICKSTART.md](QUICKSTART.md)**（10 分钟从零启动）。

---

## 目录

- [架构](#架构)
- [技术栈](#技术栈)
- [功能清单](#功能清单)
- [快速开始](#快速开始)
- [端口一览](#端口一览)
- [API 概览](#api-概览)
- [WebSocket 协议](#websocket-协议)
- [配置说明](#配置说明)
- [测试](#测试)
- [项目结构](#项目结构)
- [路线图](#路线图)

---

## 架构

```
                          ┌─────────────┐
        浏览器 (Vue 3) ──▶ │ nook-gateway │  :8080  JWT 鉴权 + 路由 + 注入 X-User-Id
                          └──────┬──────┘
            ┌──────────────┬─────┴────────┬──────────────┐
            ▼              ▼              ▼              ▼
      ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
      │ nook-auth│  │ nook-user│  │  nook-im │  │  nook-ai │
      │   :8081  │  │   :8082  │  │   :8083  │  │   :8084  │
      └────┬─────┘  └────┬─────┘  └────┬─────┘  └──────────┘
           │             │     ▲       │  (Feign 取资料/好友)
           │             └─────┼───────┘
           ▼             ▼     ▼       ▼
    ┌──────────────────────────────────────────────┐
    │  PostgreSQL · Redis · Nacos · RabbitMQ(可选)   │
    └──────────────────────────────────────────────┘
```

- **服务注册/配置**：Nacos
- **服务间调用**：OpenFeign（`nook-im` → `nook-user` 聚合成员资料、好友列表）
- **实时推送**：WebSocket（单机本地直推；多实例时经 RabbitMQ **广播 exchange** 分发保证一致）

| 模块 | 职责 |
|---|---|
| `nook-common` | 公共件：`Result` 统一响应、全局异常、JWT 工具、缓存 key、网关 header 常量 |
| `nook-gateway` | 网关：路由 + JWT 校验 + Redis token 核对 + 注入 `X-User-Id` + CORS + WS token 兼容 |
| `nook-auth` | 认证：注册/登录/登出/改密 + 多端踢出 |
| `nook-user` | 用户资料 + 好友关系全流程 |
| `nook-im` | IM：单聊/群聊会话、消息、WebSocket、在线状态、撤回、已读、文件/图片消息（RustFS 直传） |
| `nook-ai` | AI：用户私有 Agent（agentscope-harness）+ 同 owner 多 Agent 共享长期记忆，100% 入 PG |
| `nook-web` | 前端 Vue 3 SPA |

---

## 技术栈

**后端**
- JDK 25 · Spring Boot 3.5.14
- Spring Cloud 2025.0.2 · Spring Cloud Alibaba 2025.0.0.0
- 注册/配置中心 Nacos 3.x · 网关 Spring Cloud Gateway (WebFlux)
- PostgreSQL · MyBatis-Flex 1.10.9
- Redis（token 黑名单 / 在线状态 / 单端在线索引）
- RabbitMQ（默认关闭，多实例广播时开启）
- 对象存储 RustFS（S3 兼容，IM 文件消息预签名直传，AWS SDK v2）
- JWT：jjwt 0.12.6 · 密码：BCrypt
- AI：agentscope-harness 2.0.2（单例 HarnessAgent + Gateway/Channel）· DeepSeek `deepseek-v4-flash-0731`（OpenAI 兼容）

**前端（nook-web）**
- Vue 3.5 · Vite · TypeScript · Pinia · Vue Router
- Element Plus · Axios · 原生 WebSocket（心跳 + 指数退避重连）

---

## 功能清单

### 🔐 认证（nook-auth）
- 注册 / 登录 / 登出 / 查当前用户 / 改密码
- **多端踢出（单端在线）**：新登录使该用户旧端 token 立即失效；改密撤销全部端

### 👤 用户 & 好友（nook-user）
- 个人资料：查自己 / 改资料 / 按 id 查（脱敏）/ 模糊搜索
- 好友：发申请 / 收到的·发出的申请 / 接受 / 拒绝 / 列表 / 删除 / 改备注
- 内部接口：批量取资料、某用户好友 id 列表（供 IM 聚合）

### 💬 IM（nook-im）
- **单聊**：取或创建会话（幂等）、会话列表（含未读数）、发消息、历史分页、已读上报、撤回（2 分钟内 / 仅本人 / 原文脱敏）
- **群聊**：建群 / 改群名头像 / 加成员 / 踢成员 / 设·取消管理员 / 转让群主 / 退群；成员列表（跨服务聚合昵称头像角色）；完整角色权限矩阵（群主 > 管理员 > 普通）
- **成员变更系统消息**：建群/加/踢/退/转让/改角色 → 结构化 JSON 系统消息（`contentType=4`）
- **群聊已读人数**：已读 N/M
- **实时 WebSocket**：新消息 / 撤回 / 好友上线下线推送，心跳，多端会话
- **在线状态**：Redis 计数 + TTL 兜底，跳变时广播给好友
- **文件 / 图片消息**：`POST /im/files/presign` 签发 S3 预签名 PUT URL，客户端直传 RustFS（不经业务服务中转），消息 `contentType=2 图片 / 3 文件`；MIME 白名单 + 大小上限；前端按 MIME 渲染图片/视频/音频/文件气泡
- **多实例一致性**：新消息 / 撤回 / 在线状态统一走事件总线，开启 MQ 后 BROADCASTING 跨实例广播

### 🤖 AI 助手（nook-ai）
- **用户私有 Agent**：每个用户可建多个属于自己的 AI Agent，像好友一样有长期记忆；建/列/改/删，全程 owner 校验
- **共享长期记忆**：同一 owner 的多个 Agent 经 `SharedMemoryStore` 命名空间装饰器共享 `MEMORY.md`/`memory`，人格（sysPrompt）与对话会话各自独立
- **只读环境 100% 入 PG**：agentscope 官方 `PostgresBaseStore`（workspace）+ `PostgresAgentStateStore`（对话快照），不依赖任何本地文件/SQLite
- **单例 + persona 逐轮注入**：全模块一个 HarnessAgent（`sysPrompt` 留空），persona 经 `PersonaMiddleware` 随请求注入系统提示——persona 改动下一轮即生效，无需按 agentId 缓存/重建
- **对话**：会话线程（`/ai/agents/{id}/sessions`）+ 流式对话（`/ai/agents/{id}/chat/stream`，经 `ChatUiChannel.sendStream` 走 Gateway，带 per-session 排队，SSE 推 `TEXT_BLOCK_DELTA` 增量；另留同步 `/chat` 兜底）
- **模型**：DeepSeek `deepseek-v4-flash-0731`（2026-07-31 生产版，agent/推理表现优于旧 preview）；API Key 走 `nook-ai/.env`（启动加载，不入库）

### 🚪 网关（nook-gateway）
- 路由转发 + JWT 鉴权 + Redis token 校验 + 注入 `X-User-Id` / `X-Username` + CORS + WebSocket `?access_token=` 兼容 + OPTIONS 放行

---

## 快速开始

### 前置
- **JDK 25**（`JAVA_HOME` 须指向 JDK 25；机器默认可能是 21，要显式覆盖）
- Docker（起 PostgreSQL / Redis / Nacos / RabbitMQ）
- Node.js + pnpm（前端）

### 1. 起基础设施

```bat
:: Windows，一键起容器并幂等执行 SQL 建表
nook.bat up

:: 其它命令
nook.bat status   :: 查看各容器状态
nook.bat down     :: 停止
nook.bat reset    :: 清库重来（down -v + 删数据）
```

底层即 `docker-compose.yml`，包含 PostgreSQL / Redis / Nacos / RabbitMQ / RustFS（对象存储）。

> **AI 密钥**：跑 `nook-ai` 前在 `nook-ai/.env` 填 `DEEPSEEK_API_KEY=sk-xxx`（模板见 `nook-ai/.env.example`，`.env` 已 gitignore，启动经 `spring.config.import` 自动加载）。

### 2. 起后端

启动顺序：**先 `nook-auth`（其余依赖 Nacos 注册）→ `nook-gateway` → `nook-user` → `nook-im` → `nook-ai`**。

```bash
# 确保 JDK 25
set JAVA_HOME=D:\Java\jdk-25.0.2      # Windows
export JAVA_HOME=/path/to/jdk-25       # *nix

# 编译全部
./mvnw.cmd -DskipTests package

# 分别运行各服务（或在 IDE 里跑各模块的 *Application）
./mvnw.cmd -pl nook-auth spring-boot:run
./mvnw.cmd -pl nook-gateway spring-boot:run
./mvnw.cmd -pl nook-user spring-boot:run
./mvnw.cmd -pl nook-im spring-boot:run
./mvnw.cmd -pl nook-ai spring-boot:run
```

### 3. 起前端

```bash
cd nook-web
pnpm install
pnpm dev        # http://localhost:5173
```

前端默认走真实后端；要看 mock 演示，在 `nook-web/.env.local` 写 `VITE_USE_MOCK=true`。

---

## 端口一览

| 服务 | 端口 | 备注 |
|---|---|---|
| nook-gateway | 8080 | 前端唯一入口 |
| nook-auth | 8081 | |
| nook-user | 8082 | |
| nook-im | 8083 | WebSocket 在此 |
| nook-ai | 8084 | AI Agent（需配 `DEEPSEEK_API_KEY`） |
| 前端 dev | 5173 | Vite |
| PostgreSQL | 5432 | db `nook` / 用户 `nook` `nook123` |
| Redis | 6379 | 密码 `redis123` |
| Nacos | 8848 | |
| RustFS | 9000 / 9001 | S3 API / 控制台（rustfsadmin / rustfssecret） |
| RabbitMQ | 5672 / 15672 | AMQP / 管理台（nook / nook123） |

---

## API 概览

> 所有业务接口统一返回 `Result<T>`：`{ "code": 200, "message": "ok", "data": ... }`。
> 业务错误以 HTTP 200 + `code≠200` 表达（如 `3005` 撤回越权）；鉴权失败由网关返回 HTTP 401。
> 鉴权接口需带 `Authorization: Bearer <token>`，网关校验后向下游注入 `X-User-Id`。

### 认证 `/auth/*`
| Method | Path | 说明 |
|---|---|---|
| POST | `/auth/register` | `{username,password,nickname?}` → userId |
| POST | `/auth/login` | `{username,password}` → `{userId,username,nickname,token,expireSeconds}`（踢其它端） |
| POST | `/auth/logout` | 登出 |
| GET | `/auth/me` | 当前用户 |
| POST | `/auth/change-password` | `{oldPassword,newPassword}`（撤销全部端） |

### 用户 & 好友 `/user/*`
| Method | Path | 说明 |
|---|---|---|
| GET / PUT | `/user/me` | 查 / 改个人资料 |
| GET | `/user/{id}` · `/user/search?q=&limit=` | 查他人（脱敏）/ 搜索 |
| GET | `/user/friends` | 好友列表 |
| POST | `/user/friends/requests` | 发好友申请 |
| GET | `/user/friends/requests/incoming` · `/outgoing` | 收到 / 发出的申请 |
| POST | `/user/friends/requests/{id}/accept` · `/reject` | 接受 / 拒绝 |
| DELETE / PUT | `/user/friends/{friendUserId}` · `/{friendUserId}/remark` | 删好友 / 改备注 |

### IM `/im/*`
| Method | Path | 说明 |
|---|---|---|
| GET | `/im/conversations` | 我的会话列表（含未读数） |
| POST | `/im/conversations/direct` | 取或创建单聊（幂等） |
| POST | `/im/conversations/{id}/read` | 已读上报 |
| POST | `/im/conversations/group` | 建群 |
| PUT | `/im/conversations/{id}` | 改群名/头像 |
| GET | `/im/conversations/{id}/members` | 群成员（含资料/角色） |
| POST / DELETE | `/im/conversations/{id}/members` · `/members/{uid}` | 加 / 踢成员 |
| PUT | `/im/conversations/{id}/members/{uid}/role` | 设角色（群主） |
| POST | `/im/conversations/{id}/owner` · `/leave` | 转让群主 / 退群 |
| POST / GET | `/im/messages` · `/im/messages?conversationId=&beforeId=&limit=` | 发消息 / 历史 |
| POST | `/im/messages/{id}/recall` | 撤回 |
| GET | `/im/messages/{id}/read-status` | 已读人数 |
| POST | `/im/files/presign` | `{fileName,mimeType,size}` → 预签名 PUT URL（再直传 + 发文件消息） |

### AI `/ai/*`
| Method | Path | 说明 |
|---|---|---|
| POST / GET | `/ai/agents` | 建 Agent `{name,persona?,avatarUrl?,modelName?}` / 我的 Agent 列表 |
| GET / PUT / DELETE | `/ai/agents/{id}` | 详情 / 改 name·persona·avatar / 删 |
| POST / GET | `/ai/agents/{id}/sessions` | 建对话线程 `{title?}` / 线程列表 |
| POST | `/ai/agents/{id}/chat/stream` | **流式对话**（SSE）`{sessionId?,content}` → 事件 `delta{t}` / `done{sessionId,text}` / `error{message}`（缺 sessionId 自动建默认会话） |
| POST | `/ai/agents/{id}/chat` | 同步对话 `{sessionId?,content}` → `{sessionId,reply}`（兜底，非流式） |

---

## WebSocket 协议

- 端点：`ws://<gateway>/im/ws?access_token=<JWT>`（网关把 `access_token` 转成 `X-User-Id` 完成握手鉴权）
- 连接后服务端推 `{"type":"ready","userId":...}`
- 心跳：客户端发 `{"type":"ping"}` → 服务端回 `{"type":"pong","ts":...}`
- 服务端推送事件：

| type | data | 触发 |
|---|---|---|
| `message` | `MessageVO` | 新消息（含 `contentType=4` 的系统消息，其 `content` 为 `{"action":...}` JSON） |
| `recall` | `{conversationId,messageId}` | 消息被撤回 |
| `presence` | `{userId,online}` | 好友上线 / 下线 |

---

## 配置说明

- **JWT 密钥**：当前各服务 yml 写死同一个 secret，**生产务必改并统一到 Nacos 共享配置**。
- **多实例广播**：`nook.im.mq.enabled=true`（默认）时新消息/撤回/在线状态走 RabbitMQ 广播 exchange（每实例一条匿名队列，各收全量）；单机若不想起 broker 可设 `false` 走进程内本地直推。
- **Nacos 3.x**：需配 `NACOS_AUTH_TOKEN`（base64）等三件套，即使关认证也要给。
- **PostgreSQL 18+**：数据卷挂载到 `/var/lib/postgresql/data`。
- **MyBatis-Flex**：每个 ORM 模块需单独加 `spring-boot-starter-jdbc`，启动类加 `@MapperScan`。
- **AI 密钥**：`nook-ai/.env` 写 `DEEPSEEK_API_KEY`（不入库），或用同名环境变量覆盖。
- **对象存储**：`docker-compose` 的 `RUSTFS_SECRET_KEY` 须与 `nook-im` 的 `nook.storage.secret-key` 一致；bucket 由 `nook-im` 启动自建。

---

## 测试

```bash
# 全量单测（127 用例），务必带 JDK 25
JAVA_HOME=/path/to/jdk-25 ./mvnw.cmd test

# 单模块（带 -am 连依赖一起编，避免用到 .m2 里过期的 nook-common）
./mvnw.cmd -pl nook-im -am test
./mvnw.cmd -pl nook-ai -am test
```

覆盖：认证、好友、会话/消息/撤回/已读、群聊管理与权限、系统消息、成员资料聚合、在线状态、多端踢出、事件广播、全局异常、AI 共享记忆命名空间归一与 Agent CRUD/越权。

---

## 项目结构

```
nook/
├─ nook-common/     公共件
├─ nook-gateway/    网关
├─ nook-auth/       认证
├─ nook-user/       用户 + 好友
├─ nook-im/         IM（单聊/群聊/WS/在线状态/文件消息）
├─ nook-ai/         AI（用户私有 Agent + 共享记忆，agentscope-harness）
├─ nook-web/        前端 Vue 3
├─ sql/             schema 初始化（01_auth / 02_user / 03_im / 04_im 文件迁移 / 05_ai）
├─ scripts/rustfs/  RustFS 初始化（bucket + 公开读 + CORS）
├─ docker-compose.yml
└─ nook.bat         基础设施一键启停
```

> 进度详情由 `PROGRESS.md`（前端）/ `BACKEND_PROGRESS.md`（后端）记录，二者为维护者本地工作文档，不纳入版本库。

---

## 路线图

- [x] **nook-ai**：agentscope-harness 用户私有 Agent + 共享长期记忆 + 100% 入 PG（官方 PG 存储）+ DeepSeek
- [x] **AI 流式对话**：经 Gateway/Channel `sendStream` 推 `TEXT_BLOCK_DELTA` 增量，前端逐字渲染
- [x] **图片/文件消息**：RustFS 预签名直传 + `contentType 2/3` + 前端气泡渲染
- [ ] **AI 增强**：会话历史接口、memory_search 全文检索
- [ ] **会话列表去 N+1**：`ConversationVO` 增加 `lastMessageContent`
- [ ] **在线状态快照**：新连上的用户主动拉取"当前在线好友"
- [x] **可观测**：业务服务接入 actuator 健康端点
- [ ] **JWT 密钥统一**到 Nacos 共享配置
- [ ] **离线推送**（APNs / FCM）

---

## License

Copyright (c) 2026 AriesChenL

本项目基于 [GNU AGPL-3.0](LICENSE) 开源。任何人可自由使用、修改，但**改动后的版本一旦对外提供（包括作为 SaaS / 网络服务）**，必须以同一协议公开其完整源码。商业闭源使用请另行联系作者授权。

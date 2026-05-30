# Nook 后端进度

> 最后更新：2026-05-30
> 仅记录后端微服务的状态；前端进度见 `PROGRESS.md`。
> 下次"继续后端的工作"先读本文件。

---

## 1. 模块全景

```
nook/
├─ nook-common/   公共：Result / ResultCode / BusinessException / GlobalExceptionHandler / JwtUtil / CacheKeys / RequestHeaders
├─ nook-gateway/  网关：路由 + JwtAuthGlobalFilter + CORS + Actuator
├─ nook-auth/     认证：注册/登录/登出/me/改密
├─ nook-user/     用户资料 + 好友关系（申请/接受/拒绝/删除/改备注/搜索）
├─ nook-im/       IM：单聊+群聊会话/消息 REST + WebSocket 推送 + Redis 在线 + RocketMQ 广播 + 撤回
├─ nook-ai/       仅 Application.java（未做）
└─ sql/           01_init_auth / 02_init_user / 03_init_im
```

启动顺序：`nook.bat up`（docker-compose + SQL 建表）→ nook-auth → nook-gateway → nook-user → nook-im → nook-ai。

---

## 2. 已实现 API 清单

### nook-auth（端口 8081，路由 `/auth/**`）

| Method | Path | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/auth/register` | 否 | body `{username,password,nickname?}` → `Long`(userId) |
| POST | `/auth/login` | 否 | body `{username,password}` → `{userId,username,nickname,token,expireSeconds}` |
| POST | `/auth/logout` | 是 | 清 Redis token |
| GET  | `/auth/me` | 是 | 当前用户完整资料 |
| POST | `/auth/change-password` | 是 | body `{oldPassword,newPassword}`，改完撤销当前 token |

### nook-user（端口 8082，路由 `/user/**`）

| Method | Path | 说明 |
|---|---|---|
| GET    | `/user/me` | 完整资料（含 email/phone） |
| PUT    | `/user/me` | 改昵称/头像/邮箱/手机 |
| GET    | `/user/{id}` | 公开资料（脱敏） |
| GET    | `/user/search?q=&limit=` | 模糊搜索 username/nickname/email/phone |
| GET    | `/user/batch?ids=1,2,3` | 批量取公开资料（脱敏），去重限 200，供 nook-im 聚合群成员资料用 |
| GET    | `/user/friends` | 我的好友列表 |
| POST   | `/user/friends/requests` | 发好友申请 `{toUserId,message?}` |
| GET    | `/user/friends/requests/incoming` | 收到的申请 |
| GET    | `/user/friends/requests/outgoing` | 发出的申请 |
| POST   | `/user/friends/requests/{id}/accept` | 接受 |
| POST   | `/user/friends/requests/{id}/reject` | 拒绝 |
| DELETE | `/user/friends/{friendUserId}` | 删好友（双向，幂等） |
| PUT    | `/user/friends/{friendUserId}/remark` | 改备注 `{remark}` |

设计要点：`UserAccount` 实体不含 `password_hash`，nook-user 物理上无法碰密码；好友单向双写，查询零 join。

### nook-im（端口 8083，路由 `/im/**`）

REST：

| Method | Path | 说明 |
|---|---|---|
| POST   | `/im/conversations/direct` | 取或创建单聊会话（幂等） |
| GET    | `/im/conversations` | 我的会话列表（按 last_message_at 倒序，含 unreadCount） |
| GET    | `/im/conversations/{id}` | 会话详情 |
| POST   | `/im/conversations/{id}/read` | 已读上报 `{lastReadMsgId}` |
| POST   | `/im/messages` | 发消息 `{conversationId,contentType?,content}` |
| GET    | `/im/messages?conversationId=&beforeId=&limit=` | 历史消息（id desc，limit≤100） |
| POST   | `/im/messages/{id}/recall` | 撤回（仅发送者本人 + 2 分钟内） |

群聊管理（2026-05-30 新增，角色 1普通/2管理员/3群主）：

| Method | Path | 鉴权角色 | 说明 |
|---|---|---|---|
| GET    | `/im/conversations/{id}/members` | 任意成员 | 成员列表（含资料）：`[{userId,role,joinedAt,username,nickname,avatarUrl}]`，按 role 降序 / 入群时间升序 |
| POST   | `/im/conversations/group` | 任意登录用户 | 建群 `{name,avatarUrl?,memberIds[]}`，创建者自动为群主，成员去重并排除自身 |
| PUT    | `/im/conversations/{id}` | 管理员/群主 | 改群名/头像 `{name?,avatarUrl?}` |
| POST   | `/im/conversations/{id}/members` | 管理员/群主 | 加成员 `{memberIds[]}`，已在群中的跳过 |
| DELETE | `/im/conversations/{id}/members/{targetUserId}` | 管理员/群主 | 踢人；不能踢自己(走退群)/群主；管理员不能踢管理员 |
| PUT    | `/im/conversations/{id}/members/{targetUserId}/role` | 群主 | 设角色 `{role}`(仅1/2，群主用转让) |
| POST   | `/im/conversations/{id}/leave` | 任意成员 | 退群；群主须先转让 |
| POST   | `/im/conversations/{id}/owner` | 群主 | 转让群主 `{newOwnerId}`，原群主降为普通成员 |

> `ConversationVO` 新增 `myRole` 字段（当前用户在该会话中的角色，单聊恒为普通），便于前端按角色渲染管理按钮。
> 发消息/历史/撤回链路群聊与单聊完全复用（`requireMember` + `listMemberIds` + 现有 push）。
> **成员资料聚合**：`/members` 端点由 `MemberQueryService` 经 OpenFeign（`UserClient` → `lb://nook-user` 的 `/user/batch`）补昵称/头像；nook-im 不依赖 nook-user，故 Feign 响应映射到本地 `UserBriefVO`。nook-user 不可用时**优雅降级**为仅返回 userId/role，昵称头像置空，不影响主流程。会话列表 `/conversations` 仍只回 `memberIds`（避免列表页 N+1），需要资料时单独拉 `/members`。
> **成员变更会发 WS 系统消息**（content_type=4）：建群/加人/踢人/退群/转让/设角色 各发一条 `SystemMessageService.post`，内容为结构化 JSON（见下方"系统消息"），复用 `NewMessageEvent` → 多实例 MQ 广播。

WebSocket：

- 端点 `/im/ws`（走网关时浏览器用 `?access_token=` 兜底鉴权）
- 握手：`UserIdHandshakeInterceptor` 读 `X-User-Id`（缺/非数字 → 401）
- 连接后服务端推 `{type:"ready", userId}`
- 心跳：客户端发 `{"type":"ping"}` → 服务端回 `{type:"pong", ts}`
- 服务端推送两类事件：
  - `{type:"message", data: MessageVO}`
  - `{type:"recall",  data:{conversationId, messageId}}`
- **系统消息**：群成员变更走普通 `message` 事件下发，`MessageVO.contentType=4`，`content` 为结构化 JSON 字符串，前端按 `action` 渲染中文（后端不拼文案、不需昵称）：
  - `{"action":"group_created","operatorId":1}`
  - `{"action":"members_added","operatorId":1,"targetIds":[2,3]}`
  - `{"action":"member_removed","operatorId":1,"targetId":9}`（被踢者本人也会收到）
  - `{"action":"member_left","operatorId":8}`
  - `{"action":"owner_transferred","operatorId":1,"targetId":9}`
  - `{"action":"role_changed","operatorId":1,"targetId":9,"role":2}`

事件流（新消息 / 系统消息 / **撤回** 三类统一走 `MessageEventPublisher`）：
- `nook.im.mq.enabled=false`（默认）→ `LocalMessageEventPublisher` 进程内直推
- `nook.im.mq.enabled=true` → `RocketMqMessageEventPublisher` 发 MQ，**BROADCASTING** 消费，每个 nook-im 实例都收到并推本机在线 session
  - 新消息/系统消息 → topic `nook-im-new-message`（`NewMessageEvent`）
  - 撤回 → topic `nook-im-recall`（`RecallEvent`），同会话用 conversationId 分片键与新消息保序
- 发送/撤回都在 `@Transactional` 内通过 `MessageService.runAfterCommit()`（`afterCommit`）注册，保证事务可见后才推

### nook-gateway（端口 8080）

- 路由：`/auth/**` `/user/**` `/im/**` `/ai/**` → `lb://nook-*`
- 白名单：`/auth/register` `/auth/login` `/actuator/**`
- 鉴权：`JwtAuthGlobalFilter`
  - 从 `Authorization: Bearer ...` 或 `?access_token=...` 取 token
  - 验签 + Redis token 黑名单核对
  - 通过后注入 `X-User-Id` / `X-Username` 给下游
  - 放行 `OPTIONS` 预检请求
- CORS：`globalcors` 允许 `http://localhost:*` / `http://127.0.0.1:*`，含 credentials
- 健康：`/actuator/health` `/actuator/info`

---

## 3. SQL Schema

| 文件 | 表 |
|---|---|
| `sql/01_init_auth.sql` | `users` |
| `sql/02_init_user.sql` | `friendships`（owner_id/friend_id 双向单条）、`friend_requests`（status 0/1/2/3） |
| `sql/03_init_im.sql` | `conversations`（type 1单聊 2群聊）、`conversation_members`（含 last_read_msg_id / mute / role）、`messages`（含 recalled / recalled_at） |

---

## 4. 配置与基础设施

| 服务 | 端口 | 备注 |
|---|---|---|
| PostgreSQL | 5432 | db=`nook` user=`nook`/`nook123` |
| Redis | 6379 | password=`redis123` |
| Nacos | 8848 | namespace 默认；3.x 需 env 三件套 |
| RocketMQ namesrv | 9876 | 默认未启用 |
| RocketMQ broker | 10911 | proxy 18081 |
| nook-gateway | 8080 |  |
| nook-auth | 8081 |  |
| nook-user | 8082 |  |
| nook-im | 8083 |  |
| nook-ai | 8084 | 未实现 |

JWT secret 写死在三个 yml 里：`change-me-please-this-must-be-at-least-32-bytes-long-secret`。生产部署必须改且统一为外部配置。

---

## 5. 测试覆盖（98 用例全绿）

| 模块 | 文件 | 用例 |
|---|---|---:|
| nook-common | `GlobalExceptionHandlerTest` | 5 |
| nook-auth | `AuthServiceTest` | 13 |
| nook-user | `FriendServiceTest` | 13 |
| nook-user | `UserServiceTest` | 2 |
| nook-im | `ConversationServiceTest` | 17 |
| nook-im | `MemberQueryServiceTest` | 5 |
| nook-im | `MessageServiceTest` | 11 |
| nook-im | `SystemMessageServiceTest` | 3 |
| nook-im | `MessagePushServiceTest` | 4 |
| nook-im | `WebSocketSessionManagerTest` | 7 |
| nook-im | `ChatWebSocketHandlerTest` | 5 |
| nook-im | `UserIdHandshakeInterceptorTest` | 3 |
| nook-im | `LocalMessageEventPublisherTest` | 4 |
| nook-im | `RocketMqMessageEventConsumerTest` | 2 |
| nook-im | `RocketMqRecallEventConsumerTest` | 2 |
| nook-im | `NewMessageEventTest` | 1 |
| nook-im | `RecallEventTest` | 1 |
| **合计** |  | **98** |

跑全量：
```bash
JAVA_HOME=D:/Java/jdk-25.0.2 ./mvnw.cmd test
```

---

## 6. 没做的部分（下次接着干的候选）

按价值排序：

0. ~~**群聊管理**~~ ✅ 已于 2026-05-30 完成（建群/加踢/改名/退群/转让群主/设管理员，17 个单测）。
   ~~成员变更的 WS 系统消息~~ ✅ 同日完成：新建 `SystemMessageService`（独立于 `MessageService`，避免循环依赖），6 个变更点各发一条 `content_type=4` JSON 系统消息，复用 `NewMessageEvent` 走多实例 MQ 广播，3 个单测。
   ~~群成员资料聚合~~ ✅ 同日完成：`GET /im/conversations/{id}/members` 经 OpenFeign 调 nook-user `/user/batch` 补昵称/头像，降级安全，5 个单测。
   群聊已无剩余收尾项。

1. **nook-ai**（用户暂时不做，但架构留好接入点）
   - 引入 Spring AI（父 pom 已 manage `spring-ai-bom`）
   - 推荐方案：AI 作为"特殊用户"，nook-ai 订阅 `nook-im-new-message` MQ，识别接收方是 AI 用户 → 调 LLM → 通过 IM 写消息 API 回写
   - 模型选 OpenAI 兼容协议（DeepSeek / 通义 / Ollama 都行）
   - SSE 流式可选

3. **e2e 集成测试**
   - Testcontainers 起 PG/Redis
   - `@SpringBootTest` + `WebTestClient` 跑端到端

4. **API 文档**
   - springdoc-openapi 自动出 Swagger UI

5. **可观测**
   - Micrometer + Prometheus
   - 日志统一 JSON 格式

6. **细节**
   - 头像/图片上传（MinIO 或本地静态目录）
   - 消息已读人数（群聊场景）
   - 在线状态推送给好友列表
   - 多端踢出（一处登录踢另一处）

---

## 7. 2026-05-28 修复记录

| 问题 | 原因 | 修复 |
|---|---|---|
| `friend_requests` / `friendships` 表不存在 | `02_init_user.sql` / `03_init_im.sql` 未在 PG 中执行（docker-entrypoint-initdb.d 只在首次初始化时跑） | `nook.bat up` 每次启动后幂等执行所有 SQL |
| `FROM "conversations c"` 报 relation 不存在 | MyBatis-Flex `.from("conversations c")` 把整个字符串加了引号 | 去掉 `.from()` 别名，子查询改用 `conversations.id` |
| `conversation_id in` 传 List 报类型错误 | `.where("conversation_id in", convIds)` 把 List 整体当一个参数 | 手动展开占位符 `(?, ?, ...)` + `toArray()` |
| INSERT messages 时 `recalled` NOT NULL 违反 | `MessageService.send()` 未设 `recalled`，MyBatis-Flex 传 null 覆盖 DB 默认值 | 新增 `m.setRecalled((short) 0)` |
| docker-compose PG volume 路径不规范 | 挂载到 `/var/lib/postgresql` 而非 `/var/lib/postgresql/data` | 修正为官方推荐路径 |

---

## 7.5 2026-05-30 群聊管理 + 成员变更系统消息 + 成员资料聚合 + 撤回 MQ 广播

- 新增 7 个群聊 REST 接口（见第 2 节），全部走网关 `X-User-Id` 鉴权。
- 角色模型沿用 schema 既有字段：`conversation_members.role` 1普通/2管理员/3群主，`conversations.owner_id`。**无需改表**。
- 逻辑全部落在 `ConversationService`，复用既有 `addMember` / `requireMember` / `buildVO` 私有 helper；新增 `requireGroup` / `requireAdminOrOwner` / `requireOwner` / `findMember` / `dedupExclude` / `touch`。
- `ConversationVO` 加 `myRole`，`buildVO` 和 `listMine` 都填充。
- 权限矩阵：建群=任意登录用户；改名/加人/踢人=管理员或群主；设角色/转让群主=仅群主；退群=任意成员(群主须先转让)；踢人不能踢自己/群主，管理员不能踢管理员。
- 新增 `nook-common` 错误码 3008–3013（CONVERSATION_NOT_GROUP / GROUP_PERMISSION_DENIED / GROUP_MEMBER_ALREADY / GROUP_MEMBER_NOT_FOUND / GROUP_OWNER_CANNOT_LEAVE / GROUP_ROLE_INVALID）。
- 新增 `ConversationServiceTest` 17 个用例。
- **成员变更 WS 系统消息**：新建 `SystemMessageService`（依赖 MessageMapper/ConversationMapper/MessageEventPublisher/ObjectMapper，**刻意不依赖 ConversationService**，避免与 MessageService 那条链形成循环）。建群/加人/踢人/退群/转让/设角色 6 个点各 `post` 一条 `content_type=4` 的 JSON 系统消息（`{action, operatorId, ...}`），复用 `NewMessageEvent` → 自动多实例 MQ 广播。踢人场景在删除前 snapshot 成员，让被踢者本人也收到通知。新增 `SystemMessageServiceTest` 3 个用例。全量 **86 绿**。
- **群成员资料聚合**：nook-user 加 `GET /user/batch?ids=`（`UserService.listByIds`，去重限 200，脱敏 `fromPublic`）。nook-im 加 OpenFeign（`spring-cloud-starter-openfeign` + `loadbalancer`，`@EnableFeignClients(basePackages="...client")`），`UserClient` 调 `lb://nook-user`。聚合落在新 `MemberQueryService`（依赖 `ConversationService.requireMember` + `ConversationMemberMapper` + `UserClient`，**不改 ConversationService 构造器**），失败 try/catch 降级。新增 DTO `UserBriefVO`/`MemberVO`，`UserServiceTest` 2 + `MemberQueryServiceTest` 5。
- **顺手修复**：工作区 `nook-user/.../UserVO.java` 第 14 行被损坏成 `public uclUserVO {`（与本次群聊无关的未提交脏改动），已对齐 HEAD 改回 `public class UserVO {`，否则全量 `mvn test` 在 nook-user 编译就挂。`nook.bat` 的 ASCII 化改动是既有的有意改动，未触碰。
- 又一处 MyBatis-Flex 坑：`UserService.listByIds` 原想用 `selectListByIds`（BaseMapper 的 default 方法），但 Mockito 在 `when()` 里会执行其真实默认实现导致 NPE，改用既有"手动展开占位符 + `selectListByQuery`"模式（同 `in + List` 坑）。测试用 `List.of` 含 null 会 NPE，改 `Arrays.asList`。
- **撤回事件 MQ 广播**（开放问题 #8 收尾）：新建 `RecallEvent` + topic `nook-im-recall`，`MessageEventPublisher` 加 `publishRecall`，Local/Rocket 两实现 + `RocketMqRecallEventConsumer`（BROADCASTING）。`MessageService.recall` 从直调 `pushService.pushRecall` 改为走 `eventPublisher.publishRecall` 并 `runAfterCommit`（顺带把原 `publishAfterCommit(NewMessageEvent)` 泛化为 `runAfterCommit(Runnable)`，发送/撤回共用）。`MessageService` 不再依赖 `MessagePushService`（构造器 4→3 参，已同步测试）。新增 `RecallEventTest` 1 + `RocketMqRecallEventConsumerTest` 2 + `LocalMessageEventPublisherTest` +2。全量 **98 绿**。
- 测试踩坑备忘：`requireMember` 与 `findMember` 都调用 `memberMapper.selectOneByQuery`，单测用 Mockito 连续返回值 `thenReturn(operator, target)` 按调用顺序区分；跑 im 测试务必带 `-am`，否则用 `.m2` 里过期的 nook-common 导致 `NoSuchFieldError`。

---

## 8. 已知开放问题

- **JWT secret 配置统一**：现在每个模块 yml 重复写，应该挪到 Nacos 共享配置
- **消息分表**：单表 `messages` 在量大时要分表/换 MongoDB
- **多端踢出**：同一用户多端登录的 token 管理，目前没有 token-by-user 索引
- **离线推送**：未在线用户的消息只能下次拉历史，无主动推送（APNs/FCM）
- ~~**撤回事件的 MQ 广播**~~ ✅ 2026-05-30 解决：新建 `RecallEvent` 通道（topic `nook-im-recall`，BROADCASTING），撤回与新消息/系统消息走同一套 `MessageEventPublisher`，多实例一致。

---

## 9. 关键技术决策

| 决策 | 选择 | 原因 |
|---|---|---|
| 多实例消息广播 | RocketMQ BROADCASTING | Cluster 模式只有一个实例消费，其他实例上的在线用户收不到；Broadcasting 让每个实例都处理自己机器上的 session |
| 事件发布时机 | 事务 afterCommit | 避免接收方先收到通知、再 history 拉不到数据 |
| 顺序保证 | `asyncSendOrderly(conversationId)` | 同一会话内消息保序 |
| 好友存储 | 单向双写 friendships | 查我的好友列表零 join |
| 消息排序 | 全局 BIGSERIAL id | 暂未引入会话级 seq，IM 第二刀如果加 WS 强一致再考虑 |
| 鉴权透传 | Gateway 注入 `X-User-Id` header | 下游不解 JWT，零重复成本 |
| WS 鉴权 | header 优先 + `?access_token=` 兜底 | 浏览器 WebSocket API 无法自定义 header |
| 撤回时间窗口 | 2 分钟（`MessageService.RECALL_WINDOW`） | 微信式默认 |
| 群成员变更通知 | content_type=4 系统消息 + JSON body | 复用消息事件流即自动获得多实例 MQ 广播；前端按 action 渲染，后端不拼文案 |
| 撤回多实例一致 | 独立 `RecallEvent` 通道（非复用 NewMessageEvent 加 type） | 撤回 payload 与消息结构不同，独立 POJO/topic 更清晰；与新消息平行，consumer 各自广播 |
| 跨服务取资料 | OpenFeign 直连 `lb://nook-user` + 失败降级 | nook-im 不依赖 nook-user 编译期；资料是锦上添花，挂了不该拖垮成员列表 |
| 成员资料聚合时机 | 仅 `/members` 端点聚合，列表页不聚合 | 会话列表聚合所有会话所有成员资料会 N+1；列表只需 memberIds |

---

## 10. 关键文件索引

| 路径 | 用途 |
|---|---|
| `pom.xml` | 父 pom，统一版本（含 RocketMQ / Spring AI / MyBatis-Flex / JJWT） |
| `nook-common/.../RequestHeaders.java` | 网关透传 header 常量 |
| `nook-common/.../GlobalExceptionHandler.java` | 全局异常（13+ 种） |
| `nook-gateway/.../JwtAuthGlobalFilter.java` | JWT 鉴权 + WS token 兼容 + OPTIONS 放行 |
| `nook-im/.../ws/` | WS 完整链路 |
| `nook-im/.../mq/` | 事件抽象 + RocketMQ 实现 |
| `nook-im/.../service/MessageService.java` | 发消息 / 历史 / 撤回 |
| `nook-im/.../service/ConversationService.java` | 单聊 + 群聊会话/成员管理（建群/加踢/转让/角色） |
| `nook-im/.../service/SystemMessageService.java` | 群成员变更系统消息（content_type=4），独立于 MessageService 避免循环依赖 |
| `nook-im/.../service/MemberQueryService.java` | 群成员资料聚合（Feign 调 nook-user，降级安全） |
| `nook-im/.../client/UserClient.java` | OpenFeign 调 `lb://nook-user` 批量取资料 |
| `nook-user/.../UserService.java#listByIds` | `/user/batch` 批量脱敏资料 |
| `sql/*.sql` | 三套 schema |
| `nook.bat` | 一键启停基础设施（up/down/status/reset） |

---

## 11. Maven / JDK 备忘

- JDK 25 在 `D:\Java\jdk-25.0.2`；JAVA_HOME 默认指向 21，运行 mvn 前要显式覆盖
- Lombok 1.18.46（父 pom）
- MyBatis-Flex 1.10.9
- 单独 `mvn test` 命令时，`spring-boot-starter-test` 父 pom 已全局加，所有子模块默认可用
- nook-common 已加 `jakarta.servlet-api` provided，否则 GlobalExceptionHandler 编译失败连带 Lombok 不生效

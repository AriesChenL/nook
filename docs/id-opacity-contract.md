# 业务 ID 脱敏 · 实现契约（SSOT）

所有实现 agent 必须严格遵守本契约。目标：UI / URL / 前端可见的 API 响应里**不出现自增数字 ID**，改用不可枚举的 `public_id`（UUID）。

## 0. 核心原则

- **内部不变**：数据库 BIGSERIAL 主键、所有 BIGINT 外键关联（`user_id`/`sender_id`/`owner_id`/`conversation_id`/`from_user_id`/`to_user_id` 等）**全部保持数字不变**。
- **只在前端边界转换**：面向前端的 API，响应里的 id 字段输出 `public_id`（字符串）；请求路径/参数/体里收到的 `public_id` 在 Controller/Service 入口解析回数字主键。
- **网关 `X-User-Id` 保持数字**：JWT 内部仍编码数字 userId，网关注入的 `X-User-Id` 不变。服务内部、纯内部的 Feign 调用继续用数字 id。
- **JWT 主体暂保持数字**（属内部签名令牌，不在 URL/UI 展示）。本期不动网关与 JWT 主体；如需进一步把 JWT sub 也换 public_id，列为后续。

## 1. public_id 生成与存储

- 列：`public_id VARCHAR(36)`，唯一索引，NOT NULL。迁移见 `sql/06_add_public_ids.sql`（已写好，存量用 `gen_random_uuid()` 回填）。
- **新行在 Java 层生成**：实体 insert 前 `setPublicId(java.util.UUID.randomUUID().toString())`，这样 insert 后无需重查即可在响应里返回。
- 需要 public_id 的表：`users`、`friend_requests`、`conversations`、`messages`、`ai_agent`、`ai_chat_session`。
- 关联表 `friendships`、`conversation_members` **不加** public_id（通过双方 public_id 寻址）。

## 2. 解析助手（每服务自备）

每个服务对自己拥有的实体，提供 `public_id -> 数字 id` 解析：
- mybatis-flex：`QueryWrapper.create().select(...).where(PUBLIC_ID.eq(pid))` 或 mapper 方法 `Long selectIdByPublicId(String pid)`。
- 解析不到 → 抛业务异常（404 / 资源不存在），不要静默。

## 3. DTO / 字段约定（前端可见）

- 实体自身 id 字段：响应里命名仍为 `id`，**值改为 public_id 字符串**（前端类型从 number 改 string）。
- 对 user 的引用（`userId`/`senderId`/`ownerId`/`fromUserId`/`toUserId` 等）：值改为该 user 的 **public_id 字符串**，字段名不变。
- 对 conversation/message 的引用：同理用各自 public_id 字符串。
- 时间戳、计数等非 id 字段不变。

## 4. 跨服务契约（nook-im → nook-user，Feign `UserClient`）

nook-user 必须提供/调整：
- `GET /user/batch?ids=<数字,逗号>` → `UserBriefVO`，其中 **`id` 字段输出 user 的 public_id 字符串**（其余 nickname/avatar 不变）。入参 ids 仍是**数字**（im 内部持有数字 sender_id）。
- 新增 `GET /user/resolve?publicIds=<字符串,逗号>` → `List<Long>`（或返回 `Map<String,Long>`）：把前端传来的 user public_id 批量解析成数字 id，供 im 处理"和某好友建直聊""按 public_id 找人"等。
- `GET /user/friends/of/{userId}`（presence 内部用）**保持数字**，不变。

nook-im 据此：构建 MessageVO/MemberVO/ConversationVO 等前端 DTO 时，把内部数字 user 引用经 `UserClient.listByIds` 批量换成 public_id 字符串；收到前端的 user public_id 时经 `/user/resolve` 换回数字。

## 5. 各服务改动范围

### nook-auth + nook-user（Agent: backend-user）
- `users` 表实体加 `publicId`；注册时 set。
- **登录响应**(`/auth/login`) 和 `/user/me`：用户 `id`/`userId` 字段输出 public_id，**移除数字 userId**。JWT 内部仍数字（不动 TokenService 主体）。
- 好友：`/user/friends`（好友列表）每个好友的 `userId` → public_id；搜索 `/user/search` 返回用户 `id` → public_id；好友申请 `friend_requests` 加 publicId，列表里申请 `id`、`fromUserId` → public_id；接受/拒绝/发送申请的入参（申请 id、目标 userId）按 public_id 收、解析回数字。
- `/user/batch`：`UserBriefVO.id` → public_id（入参 ids 仍数字）。
- 新增 `/user/resolve?publicIds=` 解析端点。
- `/user/{id}`（单个资料）：入参改 public_id，响应 id → public_id。
- `friendIds`（presence 内部）保持数字。

### nook-im（Agent: backend-im）
- `conversations`/`messages` 加 `publicId`；新建时 set。
- **会话**：`ConversationVO.id` → 会话 public_id；`/im/conversations/{id}`、`/im/conversations/{id}/...` 路径参数改收会话 public_id 并解析；`getOrCreateDirect` 入参 peer 改收 user public_id（经 `/user/resolve` 换数字）；群 `ownerId`、成员 `MemberVO.userId` → user public_id（经 listByIds）。
- **消息**：`MessageVO.id` → 消息 public_id；`senderId` → user public_id；`conversationId` → 会话 public_id；撤回/已读等按 public_id 收。`last_read_msg_id` 等内部字段如出现在前端 DTO 也转 public_id。
- **presence**：推给前端的 presence 帧 `userId` → user public_id；`GET /im/presence/online` 返回 user public_id 列表（内部 Redis 仍数字，输出前转换）。

### nook-ai（Agent: backend-ai）
- `ai_agent`/`ai_chat_session` 加 `publicId`；新建时 set。
- Agent 接口：`/ai/agents` 返回每个 agent `id` → public_id；`/ai/agents/{id}`（含 chat、sessions、delete、update）路径参数改收 agent public_id 并解析；返回的 session `id`、`agentId` → 各自 public_id；chat 的 `sessionId`（请求/响应）→ session public_id。
- owner 用 X-User-Id（数字，内部）不变。

### nook-web 前端（最后做，消费以上契约）
- 所有 id 类型 number → string（`auth.user.id`、会话 id、消息 id、agent id、好友 userId…）。
- 路由 `/chat/:id` 的 id 现在是会话 public_id（字符串），逻辑不变。
- presence store 的 key 改 user public_id 字符串；"判断是不是自己发的"用 public_id 字符串比较；登录后存的自身标识用 `auth.user.id`(public_id)。
- ProfileView 已去掉数字 ID（无需再动）。

## 6. 验证

- 每个后端服务：`./mvnw -pl <module> -am compile` 通过（在 `/Users/lynn/work/code/java_code/nook` 下）。
- 前端：`pnpm build`（在 nook-web 下）。
- **运行时/迁移验证需真实环境**（运行的 PG + 数据）：先跑 `sql/06_add_public_ids.sql`，再端到端验证。各 agent 只需保证编译通过 + 逻辑自洽。

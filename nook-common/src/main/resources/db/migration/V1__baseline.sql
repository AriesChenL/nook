-- ============================================================
-- Nook 基线 schema（合并原 sql/01~07，含 public_id 脱敏与文件消息字段）
-- 所有服务共用同一个库 nook；由 Flyway 在应用启动时管理。
-- 对已有库：Flyway 以 baseline-on-migrate 采纳现状（跳过本 V1）；
-- 对全新库：本 V1 一次性建出当前全部表结构。
-- ============================================================

-- ───────── auth：用户 ─────────
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    public_id       VARCHAR(36)  NOT NULL,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    nickname        VARCHAR(64),
    avatar_url      VARCHAR(512),
    email           VARCHAR(128),
    phone           VARCHAR(32),
    status          SMALLINT     NOT NULL DEFAULT 1,          -- 1=正常 0=禁用
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_public_id ON users(public_id);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);

-- ───────── user：好友关系与申请 ─────────
CREATE TABLE IF NOT EXISTS friendships (
    id              BIGSERIAL PRIMARY KEY,
    owner_id        BIGINT       NOT NULL,
    friend_id       BIGINT       NOT NULL,
    remark          VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (owner_id, friend_id),
    CHECK (owner_id <> friend_id)
);
CREATE INDEX IF NOT EXISTS idx_friendships_owner ON friendships(owner_id);

CREATE TABLE IF NOT EXISTS friend_requests (
    id              BIGSERIAL PRIMARY KEY,
    public_id       VARCHAR(36)  NOT NULL,
    from_user_id    BIGINT       NOT NULL,
    to_user_id      BIGINT       NOT NULL,
    message         VARCHAR(255),
    status          SMALLINT     NOT NULL DEFAULT 0,          -- 0=待处理 1=已接受 2=已拒绝 3=已撤回
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (from_user_id <> to_user_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_friend_requests_public_id ON friend_requests(public_id);
CREATE INDEX IF NOT EXISTS idx_friend_req_to   ON friend_requests(to_user_id, status);
CREATE INDEX IF NOT EXISTS idx_friend_req_from ON friend_requests(from_user_id, status);

-- ───────── im：会话 / 成员 / 消息 ─────────
CREATE TABLE IF NOT EXISTS conversations (
    id                BIGSERIAL PRIMARY KEY,
    public_id         VARCHAR(36)  NOT NULL,
    type              SMALLINT     NOT NULL,                  -- 1=单聊 2=群聊
    name              VARCHAR(128),
    avatar_url        VARCHAR(512),
    owner_id          BIGINT,
    last_message_id   BIGINT,
    last_message_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversations_public_id ON conversations(public_id);

CREATE TABLE IF NOT EXISTS conversation_members (
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    role              SMALLINT     NOT NULL DEFAULT 1,        -- 1=普通 2=管理员 3=群主
    last_read_msg_id  BIGINT       NOT NULL DEFAULT 0,
    mute              SMALLINT     NOT NULL DEFAULT 0,
    joined_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_conv_member_user ON conversation_members(user_id);

CREATE TABLE IF NOT EXISTS messages (
    id                BIGSERIAL PRIMARY KEY,
    public_id         VARCHAR(36)  NOT NULL,
    conversation_id   BIGINT       NOT NULL,
    sender_id         BIGINT       NOT NULL,
    content_type      SMALLINT     NOT NULL DEFAULT 1,        -- 1=text 2=image 3=file 4=system
    content           TEXT         NOT NULL,
    file_url          VARCHAR(1024),
    file_name         VARCHAR(255),
    file_size         BIGINT,
    media_type        VARCHAR(128),
    recalled          SMALLINT     NOT NULL DEFAULT 0,        -- 0=正常 1=已撤回
    recalled_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_messages_public_id ON messages(public_id);
CREATE INDEX IF NOT EXISTS idx_messages_conv_id ON messages(conversation_id, id DESC);

-- ───────── ai：Agent / 会话 / 可见历史消息 / agentscope KV ─────────
CREATE TABLE IF NOT EXISTS agentscope_store (
  namespace_path VARCHAR(1024) NOT NULL,
  item_key       VARCHAR(512)  NOT NULL,
  value_json     TEXT          NOT NULL,
  version        BIGINT        NOT NULL,
  updated_at     BIGINT        NOT NULL,
  PRIMARY KEY (namespace_path, item_key)
);

CREATE TABLE IF NOT EXISTS ai_agent (
  id            BIGSERIAL    PRIMARY KEY,
  public_id     VARCHAR(36)  NOT NULL,
  owner_user_id BIGINT       NOT NULL,
  name          VARCHAR(64)  NOT NULL,
  persona       TEXT         NOT NULL DEFAULT '',
  avatar_url    VARCHAR(512),
  model_name    VARCHAR(64)  NOT NULL DEFAULT 'deepseek-v4-flash',
  status        SMALLINT     NOT NULL DEFAULT 1,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_agent_public_id ON ai_agent(public_id);
CREATE INDEX IF NOT EXISTS idx_ai_agent_owner ON ai_agent(owner_user_id);

CREATE TABLE IF NOT EXISTS ai_chat_session (
  id            BIGSERIAL    PRIMARY KEY,
  public_id     VARCHAR(36)  NOT NULL,
  agent_id      BIGINT       NOT NULL,
  owner_user_id BIGINT       NOT NULL,
  title         VARCHAR(128),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_chat_session_public_id ON ai_chat_session(public_id);
CREATE INDEX IF NOT EXISTS idx_ai_chat_session_agent ON ai_chat_session(agent_id);

CREATE TABLE IF NOT EXISTS ai_message (
  id          BIGSERIAL    PRIMARY KEY,
  public_id   VARCHAR(36)  NOT NULL,
  session_id  BIGINT       NOT NULL,
  role        VARCHAR(16)  NOT NULL,                          -- user | assistant
  content     TEXT         NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_message_public_id ON ai_message(public_id);
CREATE INDEX IF NOT EXISTS idx_ai_message_session ON ai_message(session_id, id);

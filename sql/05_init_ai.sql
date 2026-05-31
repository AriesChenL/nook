-- nook-ai：用户私有 AI Agent
-- 设计：每个用户可创建多个 Agent（像好友一样有长期记忆）；同一 owner 的多个 Agent
--       共享长期事实记忆（MEMORY.md / memory/），但人格与对话历史各 Agent 独立。
--
-- 注意：agentscope HarnessAgent 的工作区内容（MEMORY.md / memory/ / sessions JSONL 等）
--       与会话状态快照不落本地磁盘，全部经 PgBaseStore 写入下表 `agentscope_store`。
--       该表也由 nook-ai 启动时 PgBaseStore 幂等创建（CREATE TABLE IF NOT EXISTS），
--       此处一并建表以符合 SQL-first 惯例（命名空间段用 0x1F 连接，前缀 LIKE 检索）。

CREATE TABLE IF NOT EXISTS agentscope_store (
  namespace_path VARCHAR(1024) NOT NULL,
  item_key       VARCHAR(512)  NOT NULL,
  value_json     TEXT          NOT NULL,
  version        BIGINT        NOT NULL,
  updated_at     BIGINT        NOT NULL,
  PRIMARY KEY (namespace_path, item_key)
);

-- 业务表：用户的 Agent
CREATE TABLE IF NOT EXISTS ai_agent (
  id            BIGSERIAL    PRIMARY KEY,
  owner_user_id BIGINT       NOT NULL,
  name          VARCHAR(64)  NOT NULL,
  persona       TEXT         NOT NULL DEFAULT '',           -- 人格设定，注入 sysPrompt
  avatar_url    VARCHAR(512),
  model_name    VARCHAR(64)  NOT NULL DEFAULT 'deepseek-v4-flash',
  status        SMALLINT     NOT NULL DEFAULT 1,            -- 1 正常 / 0 停用
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ai_agent_owner ON ai_agent(owner_user_id);

-- 对话会话（前端可为同一 Agent 建多个对话线程；agentscope 用 sessionId 隔离上下文）
CREATE TABLE IF NOT EXISTS ai_chat_session (
  id            BIGSERIAL    PRIMARY KEY,
  agent_id      BIGINT       NOT NULL,
  owner_user_id BIGINT       NOT NULL,
  title         VARCHAR(128),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ai_chat_session_agent ON ai_chat_session(agent_id);

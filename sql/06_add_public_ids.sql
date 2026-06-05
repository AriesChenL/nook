-- ============================================================
-- 业务 ID 脱敏：为对外实体增加不可枚举的 public_id（UUID 文本）
-- 内部仍用 BIGSERIAL 主键 + BIGINT 外键做关联，public_id 只用于 API/URL/UI 边界。
-- 幂等：可重复执行。gen_random_uuid() 为 PostgreSQL 13+ 内置。
-- ============================================================

-- 通用步骤：加可空列 → 回填存量 → 置 NOT NULL → 建唯一索引

-- users（auth/user 共用）
ALTER TABLE users            ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);
UPDATE users            SET public_id = gen_random_uuid()::text WHERE public_id IS NULL;
ALTER TABLE users            ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_public_id            ON users(public_id);

-- friend_requests
ALTER TABLE friend_requests  ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);
UPDATE friend_requests  SET public_id = gen_random_uuid()::text WHERE public_id IS NULL;
ALTER TABLE friend_requests  ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_friend_requests_public_id  ON friend_requests(public_id);

-- conversations（聊天室 URL 用它）
ALTER TABLE conversations    ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);
UPDATE conversations    SET public_id = gen_random_uuid()::text WHERE public_id IS NULL;
ALTER TABLE conversations    ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversations_public_id    ON conversations(public_id);

-- messages
ALTER TABLE messages         ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);
UPDATE messages         SET public_id = gen_random_uuid()::text WHERE public_id IS NULL;
ALTER TABLE messages         ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_messages_public_id         ON messages(public_id);

-- ai_agent
ALTER TABLE ai_agent         ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);
UPDATE ai_agent         SET public_id = gen_random_uuid()::text WHERE public_id IS NULL;
ALTER TABLE ai_agent         ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_agent_public_id         ON ai_agent(public_id);

-- ai_chat_session
ALTER TABLE ai_chat_session  ADD COLUMN IF NOT EXISTS public_id VARCHAR(36);
UPDATE ai_chat_session  SET public_id = gen_random_uuid()::text WHERE public_id IS NULL;
ALTER TABLE ai_chat_session  ALTER COLUMN public_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_chat_session_public_id  ON ai_chat_session(public_id);

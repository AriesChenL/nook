-- Nook IM schema：会话、会话成员、消息

-- 会话
CREATE TABLE IF NOT EXISTS conversations (
    id                BIGSERIAL PRIMARY KEY,
    type              SMALLINT     NOT NULL,                  -- 1=单聊 2=群聊
    name              VARCHAR(128),                           -- 群聊名称
    avatar_url        VARCHAR(512),
    owner_id          BIGINT,                                 -- 群主/创建者；单聊为 NULL
    last_message_id   BIGINT,
    last_message_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE  conversations              IS '会话表（单聊/群聊）';
COMMENT ON COLUMN conversations.type         IS '1=单聊 2=群聊';

-- 会话成员（同一用户在一个会话内最多一条）
CREATE TABLE IF NOT EXISTS conversation_members (
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    role              SMALLINT     NOT NULL DEFAULT 1,        -- 1=普通 2=管理员 3=群主
    last_read_msg_id  BIGINT       NOT NULL DEFAULT 0,
    mute              SMALLINT     NOT NULL DEFAULT 0,        -- 0=否 1=是
    joined_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (conversation_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_conv_member_user ON conversation_members(user_id);
COMMENT ON TABLE  conversation_members              IS '会话成员表';
COMMENT ON COLUMN conversation_members.role         IS '1=普通 2=管理员 3=群主';

-- 消息
CREATE TABLE IF NOT EXISTS messages (
    id                BIGSERIAL PRIMARY KEY,
    conversation_id   BIGINT       NOT NULL,
    sender_id         BIGINT       NOT NULL,
    content_type      SMALLINT     NOT NULL DEFAULT 1,        -- 1=text 2=image 3=file 4=system
    content           TEXT         NOT NULL,
    file_url          VARCHAR(1024),                          -- 文件消息：下载/预览地址
    file_name         VARCHAR(255),                           -- 原始文件名
    file_size         BIGINT,                                 -- 字节大小
    media_type        VARCHAR(128),                           -- MIME 类型
    recalled          SMALLINT     NOT NULL DEFAULT 0,        -- 0=正常 1=已撤回
    recalled_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_messages_conv_id ON messages(conversation_id, id DESC);
COMMENT ON TABLE  messages              IS '消息表';
COMMENT ON COLUMN messages.content_type IS '1=text 2=image 3=file 4=system';
COMMENT ON COLUMN messages.media_type   IS '文件消息 MIME，前端据此渲染图片/视频/音频/文件';

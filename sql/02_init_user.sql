-- Nook user schema: 好友关系与好友申请

-- 好友关系（双向：A 加 B 成功后写两条，便于按 owner_id 单向查询好友列表）
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

COMMENT ON TABLE  friendships          IS '好友关系表（已建立的好友，单向一条记录）';
COMMENT ON COLUMN friendships.owner_id IS '记录所有者用户 ID';
COMMENT ON COLUMN friendships.friend_id IS '好友用户 ID';

-- 好友申请
CREATE TABLE IF NOT EXISTS friend_requests (
    id              BIGSERIAL PRIMARY KEY,
    from_user_id    BIGINT       NOT NULL,
    to_user_id      BIGINT       NOT NULL,
    message         VARCHAR(255),
    status          SMALLINT     NOT NULL DEFAULT 0,        -- 0=待处理 1=已接受 2=已拒绝 3=已撤回
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (from_user_id <> to_user_id)
);
CREATE INDEX IF NOT EXISTS idx_friend_req_to     ON friend_requests(to_user_id, status);
CREATE INDEX IF NOT EXISTS idx_friend_req_from   ON friend_requests(from_user_id, status);

COMMENT ON TABLE  friend_requests              IS '好友申请表';
COMMENT ON COLUMN friend_requests.status       IS '0=待处理 1=已接受 2=已拒绝 3=已撤回';

-- Nook auth schema
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    nickname        VARCHAR(64),
    avatar_url      VARCHAR(512),
    email           VARCHAR(128),
    phone           VARCHAR(32),
    status          SMALLINT     NOT NULL DEFAULT 1,        -- 1=正常 0=禁用
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);

COMMENT ON TABLE  users               IS '用户账号表';
COMMENT ON COLUMN users.username      IS '登录用户名（唯一）';
COMMENT ON COLUMN users.password_hash IS 'BCrypt 密码哈希';
COMMENT ON COLUMN users.status        IS '账号状态 1=正常 0=禁用';

-- ============================================================
-- nook-pay：Stripe 支付 / 订阅
-- ============================================================

-- ───────── 一次性付款订单 ─────────
CREATE TABLE IF NOT EXISTS payment_orders (
    id                        BIGSERIAL PRIMARY KEY,
    public_id                 VARCHAR(36)  NOT NULL,
    user_id                   BIGINT       NOT NULL,
    product_code              VARCHAR(64)  NOT NULL,
    quantity                  INT          NOT NULL DEFAULT 1,
    amount_total              BIGINT,                          -- 最小货币单位（如分），回调写回
    currency                  VARCHAR(8),
    stripe_session_id         VARCHAR(255),
    stripe_payment_intent_id  VARCHAR(255),
    status                    VARCHAR(16)  NOT NULL,           -- CREATED / PAID / EXPIRED / FAILED
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    paid_at                   TIMESTAMPTZ
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_orders_public_id ON payment_orders(public_id);
CREATE INDEX IF NOT EXISTS idx_payment_orders_user ON payment_orders(user_id);
CREATE INDEX IF NOT EXISTS idx_payment_orders_session ON payment_orders(stripe_session_id);

-- ───────── 用户 -> Stripe Customer 映射 ─────────
CREATE TABLE IF NOT EXISTS stripe_customers (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    stripe_customer_id  VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_stripe_customers_cid ON stripe_customers(stripe_customer_id);

-- ───────── 订阅 ─────────
CREATE TABLE IF NOT EXISTS subscriptions (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT       NOT NULL,
    stripe_customer_id      VARCHAR(255) NOT NULL,
    stripe_subscription_id  VARCHAR(255) NOT NULL,
    price_id                VARCHAR(255),
    status                  VARCHAR(32)  NOT NULL,             -- active / trialing / past_due / canceled ...
    current_period_end      TIMESTAMPTZ,
    cancel_at_period_end    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_subscriptions_sub_id ON subscriptions(stripe_subscription_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_user ON subscriptions(user_id);

-- ───────── Webhook 事件去重 ─────────
CREATE TABLE IF NOT EXISTS stripe_events (
    id           BIGSERIAL PRIMARY KEY,
    event_id     VARCHAR(255) NOT NULL,
    type         VARCHAR(64)  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (event_id)
);

-- ============================================================
-- nook-pay: production subscription billing state
-- ============================================================

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS product_code VARCHAR(64);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS latest_invoice_id VARCHAR(255);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS trial_end TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS canceled_at TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS last_event_created BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_subscriptions_user_updated
    ON subscriptions(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS payment_checkout_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL,
    product_code        VARCHAR(64)  NOT NULL,
    stripe_session_id   VARCHAR(255) NOT NULL,
    stripe_customer_id  VARCHAR(255) NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    payment_status      VARCHAR(32),
    checkout_url        TEXT,
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, idempotency_key),
    UNIQUE (stripe_session_id)
);
CREATE INDEX IF NOT EXISTS idx_checkout_sessions_user_created
    ON payment_checkout_sessions(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS payment_invoices (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT       NOT NULL,
    stripe_invoice_id       VARCHAR(255) NOT NULL,
    stripe_subscription_id  VARCHAR(255),
    invoice_number          VARCHAR(128),
    status                  VARCHAR(32)  NOT NULL,
    billing_reason          VARCHAR(64),
    currency                VARCHAR(8),
    amount_due              BIGINT,
    amount_paid             BIGINT,
    hosted_invoice_url      TEXT,
    invoice_pdf             TEXT,
    period_start            TIMESTAMPTZ,
    period_end              TIMESTAMPTZ,
    paid_at                 TIMESTAMPTZ,
    last_event_type         VARCHAR(64),
    last_event_created      BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (stripe_invoice_id)
);
CREATE INDEX IF NOT EXISTS idx_payment_invoices_user_created
    ON payment_invoices(user_id, created_at DESC);

ALTER TABLE stripe_events ADD COLUMN IF NOT EXISTS object_id VARCHAR(255);
ALTER TABLE stripe_events ADD COLUMN IF NOT EXISTS event_created BIGINT;
ALTER TABLE stripe_events ADD COLUMN IF NOT EXISTS livemode BOOLEAN;
ALTER TABLE stripe_events ADD COLUMN IF NOT EXISTS processed_at TIMESTAMPTZ;

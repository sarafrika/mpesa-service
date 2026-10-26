-- Outbound webhooks: where a product wants payment events sent, and every attempt to send one.
-- The signing secret is stored encrypted (enc:v1:...) and is never returned by the API.
CREATE TABLE webhook_subscriptions
(
    id                BIGSERIAL PRIMARY KEY,
    uuid              UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    client_id         VARCHAR(100) NOT NULL,
    url               VARCHAR(500) NOT NULL,
    secret            TEXT         NOT NULL,
    event_types       VARCHAR(500) NOT NULL        DEFAULT '',
    active            BOOLEAN      NOT NULL        DEFAULT true,
    secret_rotated_at TIMESTAMP,
    created_at        TIMESTAMP                    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP                    DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    deleted_at        TIMESTAMP
);

CREATE INDEX idx_webhook_subscriptions_client ON webhook_subscriptions (client_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_webhook_subscriptions_created_at ON webhook_subscriptions (created_at);

CREATE TABLE webhook_deliveries
(
    id                BIGSERIAL PRIMARY KEY,
    uuid              UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    subscription_uuid UUID         NOT NULL REFERENCES webhook_subscriptions (uuid),
    client_id         VARCHAR(100) NOT NULL,
    event_id          UUID         NOT NULL,
    event_type        VARCHAR(50)  NOT NULL,
    reference         VARCHAR(100),
    payload           JSONB        NOT NULL,
    attempt           INTEGER      NOT NULL DEFAULT 0,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    response_code     INTEGER,
    latency_ms        BIGINT,
    next_attempt_at   TIMESTAMPTZ,
    error             VARCHAR(1000),
    delivered_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- The poller claims due rows with FOR UPDATE SKIP LOCKED; the partial index keeps that cheap.
CREATE INDEX idx_webhook_deliveries_due ON webhook_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_webhook_deliveries_created_at ON webhook_deliveries (created_at DESC, id DESC);
CREATE INDEX idx_webhook_deliveries_subscription ON webhook_deliveries (subscription_uuid, created_at DESC);
CREATE INDEX idx_webhook_deliveries_client ON webhook_deliveries (client_id, created_at DESC);
CREATE INDEX idx_webhook_deliveries_status ON webhook_deliveries (status, created_at DESC);
CREATE INDEX idx_webhook_deliveries_event_type ON webhook_deliveries (event_type, created_at DESC);
CREATE INDEX idx_webhook_deliveries_latency ON webhook_deliveries (latency_ms);

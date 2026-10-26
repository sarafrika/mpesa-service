-- Every request Safaricom sends to /api/v1/callbacks/**, stored raw before its handler runs, with how
-- processing went. Bodies over the configured cap are not kept (body_truncated) and cannot be replayed.
CREATE TABLE callback_inbox
(
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    endpoint        VARCHAR(200) NOT NULL,
    kind            VARCHAR(40)  NOT NULL,
    source_ip       VARCHAR(64),
    headers         JSONB,
    body            JSONB,
    body_size       INTEGER      NOT NULL DEFAULT 0,
    body_truncated  BOOLEAN      NOT NULL DEFAULT false,
    reference       VARCHAR(500),
    status          VARCHAR(20)  NOT NULL DEFAULT 'RECEIVED'
        CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'REPLAYED')),
    error           VARCHAR(500),
    attempts        INTEGER      NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    payment_uuid    UUID,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_callback_inbox_received ON callback_inbox (received_at DESC, id DESC);
CREATE INDEX idx_callback_inbox_status ON callback_inbox (status, received_at DESC);
CREATE INDEX idx_callback_inbox_kind ON callback_inbox (kind, received_at DESC);
CREATE INDEX idx_callback_inbox_attempts ON callback_inbox (attempts);

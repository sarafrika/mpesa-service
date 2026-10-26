-- A bulk payout uploaded as CSV: one batch per BULK_PAYOUT approval, one item per valid row.
-- Each item pays through the B2C payout service with the row's reference as client_reference, so a
-- resumed batch never pays a row twice.
CREATE TABLE payout_batches
(
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    approval_uuid   UUID           NOT NULL UNIQUE REFERENCES approval_requests (uuid),
    shortcode_uuid  UUID           NOT NULL REFERENCES mpesa_shortcodes (uuid),
    title           VARCHAR(200)   NOT NULL,
    file_name       VARCHAR(255),
    status          VARCHAR(20)    NOT NULL DEFAULT 'WAITING'
        CHECK (status IN ('WAITING', 'REJECTED', 'EXPIRED', 'RUNNING', 'COMPLETED')),
    item_count      INTEGER        NOT NULL,
    invalid_count   INTEGER        NOT NULL DEFAULT 0,
    total_amount    NUMERIC(14, 2) NOT NULL,
    processed_count INTEGER        NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_by      VARCHAR(100),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payout_batches_status ON payout_batches (status);

CREATE TABLE payout_batch_items
(
    id           BIGSERIAL PRIMARY KEY,
    uuid         UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    batch_id     BIGINT         NOT NULL REFERENCES payout_batches (id),
    line_number  INTEGER        NOT NULL,
    phone_number VARCHAR(12)    NOT NULL,
    amount       NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    reference    VARCHAR(100)   NOT NULL,
    name         VARCHAR(100),
    status       VARCHAR(20)    NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'SENT', 'REFUSED', 'ERROR')),
    payout_uuid  UUID,
    message      VARCHAR(500),
    processed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payout_batch_items_reference UNIQUE (batch_id, reference)
);

CREATE INDEX idx_payout_batch_items_line ON payout_batch_items (batch_id, line_number, id);
CREATE INDEX idx_payout_batch_items_amount ON payout_batch_items (batch_id, amount, id);
CREATE INDEX idx_payout_batch_items_reference ON payout_batch_items (reference);
CREATE INDEX idx_payout_batch_items_payout ON payout_batch_items (payout_uuid);

-- The upload refuses a reference already paid this month under any client.
CREATE INDEX IF NOT EXISTS idx_mpesa_outgoing_payments_reference
    ON mpesa_outgoing_payments (client_reference, created_at);

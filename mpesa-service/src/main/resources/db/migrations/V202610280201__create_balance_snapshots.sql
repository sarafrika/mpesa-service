-- Each Account Balance query sent to Safaricom and the balances its result reported. Daraja answers on the
-- result URL later, so a row starts PENDING and is matched back by its conversation ids.
CREATE TABLE balance_snapshots
(
    id                         BIGSERIAL PRIMARY KEY,
    uuid                       UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid             UUID        NOT NULL REFERENCES mpesa_shortcodes (uuid),
    status                     VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'TIMED_OUT')),
    source                     VARCHAR(20) NOT NULL CHECK (source IN ('MANUAL', 'SCHEDULED')),
    originator_conversation_id VARCHAR(100),
    conversation_id            VARCHAR(100),
    result_code                INTEGER,
    result_desc                VARCHAR(500),
    accounts                   JSONB,
    utility_available          NUMERIC(15, 2),
    working_available          NUMERIC(15, 2),
    charges_paid_available     NUMERIC(15, 2),
    raw                        JSONB,
    requested_at               TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    received_at                TIMESTAMP,
    created_at                 TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    created_by                 VARCHAR(255),
    updated_by                 VARCHAR(255),
    deleted_at                 TIMESTAMP
);

CREATE INDEX idx_balance_snapshots_shortcode_received ON balance_snapshots (shortcode_uuid, received_at DESC)
    WHERE status = 'COMPLETED' AND deleted_at IS NULL;
CREATE INDEX idx_balance_snapshots_shortcode_requested ON balance_snapshots (shortcode_uuid, requested_at DESC);
CREATE INDEX idx_balance_snapshots_conversation ON balance_snapshots (conversation_id);
CREATE INDEX idx_balance_snapshots_originator ON balance_snapshots (originator_conversation_id);

-- Null falls back to mpesa.alerts.low-float. next_balance_query_at lets the scheduled balance query
-- claim each shortcode once per interval, whichever instance gets there first.
ALTER TABLE mpesa_shortcodes ADD COLUMN low_float_threshold NUMERIC(15, 2);
ALTER TABLE mpesa_shortcodes ADD COLUMN next_balance_query_at TIMESTAMP;

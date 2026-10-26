-- Where a shortcode may sweep money with B2B: another paybill (BusinessPayBill) or a till (BusinessBuyGoods).
CREATE TABLE b2b_destinations
(
    id                 BIGSERIAL PRIMARY KEY,
    uuid               UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid     UUID         NOT NULL REFERENCES mpesa_shortcodes (uuid),
    name               VARCHAR(100) NOT NULL,
    receiver_shortcode VARCHAR(20)  NOT NULL,
    receiver_type      VARCHAR(10)  NOT NULL CHECK (receiver_type IN ('PAYBILL', 'TILL')),
    account_reference  VARCHAR(20),
    active             BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    deleted_at         TIMESTAMP
);

CREATE INDEX idx_b2b_destinations_shortcode_name ON b2b_destinations (shortcode_uuid, name, id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_b2b_destinations_shortcode_created ON b2b_destinations (shortcode_uuid, created_at, id DESC)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_b2b_destinations_shortcode_name ON b2b_destinations (shortcode_uuid, lower(name))
    WHERE deleted_at IS NULL;

-- One row per approved sweep sent to Safaricom, unique per approval request so it is sent at most once.
-- A timeout leaves the outcome unknown: the row stays PENDING and is never resent automatically.
CREATE TABLE b2b_transfers
(
    id                         BIGSERIAL PRIMARY KEY,
    uuid                       UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid             UUID           NOT NULL REFERENCES mpesa_shortcodes (uuid),
    destination_uuid           UUID           NOT NULL REFERENCES b2b_destinations (uuid),
    approval_request_uuid      UUID           NOT NULL UNIQUE,
    command_id                 VARCHAR(30)    NOT NULL CHECK (command_id IN ('BusinessPayBill', 'BusinessBuyGoods')),
    receiver_shortcode         VARCHAR(20)    NOT NULL,
    account_reference          VARCHAR(20),
    amount                     NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    reference                  VARCHAR(40)    NOT NULL,
    remarks                    VARCHAR(100),
    status                     VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    outcome_unknown            BOOLEAN        NOT NULL DEFAULT false,
    originator_conversation_id VARCHAR(100),
    conversation_id            VARCHAR(100),
    transaction_id             VARCHAR(50),
    result_code                INTEGER,
    result_desc                VARCHAR(500),
    raw_result                 JSONB,
    maker                      VARCHAR(255),
    checker                    VARCHAR(255),
    timed_out_at               TIMESTAMP,
    completed_at               TIMESTAMP,
    created_at                 TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    created_by                 VARCHAR(255),
    updated_by                 VARCHAR(255),
    deleted_at                 TIMESTAMP
);

CREATE INDEX idx_b2b_transfers_shortcode_created ON b2b_transfers (shortcode_uuid, created_at DESC, id DESC);
CREATE INDEX idx_b2b_transfers_shortcode_amount ON b2b_transfers (shortcode_uuid, amount, id DESC);
CREATE INDEX idx_b2b_transfers_conversation ON b2b_transfers (conversation_id);
CREATE INDEX idx_b2b_transfers_originator ON b2b_transfers (originator_conversation_id);

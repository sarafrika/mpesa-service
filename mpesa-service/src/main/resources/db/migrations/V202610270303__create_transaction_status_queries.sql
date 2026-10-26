-- Each time someone (or the STK job) asks Safaricom for a payment's status. Transaction Status answers
-- later on the result URL with the same conversation ids; an STK query answers at once.
CREATE TABLE transaction_status_queries
(
    id                         BIGSERIAL PRIMARY KEY,
    uuid                       UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    payment_uuid               UUID        NOT NULL,
    direction                  VARCHAR(3)  NOT NULL CHECK (direction IN ('IN', 'OUT')),
    shortcode_uuid             UUID REFERENCES mpesa_shortcodes (uuid),
    method                     VARCHAR(20) NOT NULL CHECK (method IN ('TRANSACTION_STATUS', 'STK_QUERY')),
    status                     VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'TIMED_OUT')),
    queried_transaction_id     VARCHAR(50),
    conversation_id            VARCHAR(100),
    originator_conversation_id VARCHAR(100),
    result_code                INTEGER,
    result_desc                VARCHAR(500),
    transaction_status         VARCHAR(50),
    receipt                    VARCHAR(50),
    raw_result                 JSONB,
    completed_at               TIMESTAMP,
    created_at                 TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    created_by                 VARCHAR(255),
    updated_by                 VARCHAR(255),
    deleted_at                 TIMESTAMP
);

CREATE INDEX idx_transaction_status_queries_payment ON transaction_status_queries (payment_uuid, created_at DESC);
CREATE INDEX idx_transaction_status_queries_conversation ON transaction_status_queries (conversation_id);
CREATE INDEX idx_transaction_status_queries_originator ON transaction_status_queries (originator_conversation_id);

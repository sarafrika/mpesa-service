-- Approved Tax Remittance and Business to Pochi payments, one row per approval so each is sent at most once.
-- A timeout leaves the outcome unknown: the row stays PENDING and is never resent automatically.
CREATE TABLE product_payouts
(
    id                         BIGSERIAL PRIMARY KEY,
    uuid                       UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid             UUID           NOT NULL REFERENCES mpesa_shortcodes (uuid),
    product                    VARCHAR(40)    NOT NULL CHECK (product IN ('TAX_REMITTANCE', 'BUSINESS_TO_POCHI')),
    approval_request_uuid      UUID           NOT NULL UNIQUE,
    command_id                 VARCHAR(30)    NOT NULL CHECK (command_id IN ('PayTaxToKRA', 'BusinessPayToPochi')),
    party_b                    VARCHAR(20)    NOT NULL,
    account_reference          VARCHAR(50),
    amount                     NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    remarks                    VARCHAR(100)   NOT NULL,
    occasion                   VARCHAR(100),
    status                     VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    outcome_unknown            BOOLEAN        NOT NULL DEFAULT false,
    originator_conversation_id VARCHAR(100) UNIQUE,
    conversation_id            VARCHAR(100),
    transaction_id             VARCHAR(50),
    result_code                VARCHAR(20),
    result_desc                VARCHAR(500),
    receiver_name              VARCHAR(200),
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

CREATE INDEX idx_product_payouts_shortcode_created ON product_payouts (shortcode_uuid, product, created_at DESC, id DESC);
CREATE INDEX idx_product_payouts_shortcode_amount ON product_payouts (shortcode_uuid, product, amount, id DESC);
CREATE INDEX idx_product_payouts_conversation ON product_payouts (conversation_id);

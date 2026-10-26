-- A reversal of a received payment (Daraja Reversal API), from the maker's request to Safaricom's
-- result. At most one reversal per payment may be open or done; a failed or cancelled one frees it.
CREATE TABLE reversal_requests
(
    id                         BIGSERIAL PRIMARY KEY,
    uuid                       UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    approval_uuid              UUID           NOT NULL UNIQUE REFERENCES approval_requests (uuid),
    payment_uuid               UUID           NOT NULL REFERENCES mpesa_incoming_payments (uuid),
    shortcode_uuid             UUID           NOT NULL REFERENCES mpesa_shortcodes (uuid),
    transaction_id             VARCHAR(50)    NOT NULL,
    amount                     NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    original_amount            NUMERIC(14, 2) NOT NULL,
    status                     VARCHAR(20)    NOT NULL DEFAULT 'WAITING_APPROVAL'
        CHECK (status IN ('WAITING_APPROVAL', 'CANCELLED', 'SENDING', 'SENT', 'OUTCOME_UNKNOWN',
                          'REVERSED', 'FAILED')),
    requested_by               VARCHAR(100)   NOT NULL,
    originator_conversation_id VARCHAR(100),
    conversation_id            VARCHAR(100),
    result_code                VARCHAR(20),
    result_desc                VARCHAR(500),
    reversal_transaction_id    VARCHAR(50),
    raw_callback               JSONB,
    sent_at                    TIMESTAMPTZ,
    timed_out_at               TIMESTAMPTZ,
    completed_at               TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_reversal_requests_open ON reversal_requests (payment_uuid)
    WHERE status NOT IN ('CANCELLED', 'FAILED');
CREATE INDEX idx_reversal_requests_payment ON reversal_requests (payment_uuid, created_at);
CREATE INDEX idx_reversal_requests_originator ON reversal_requests (originator_conversation_id);
CREATE INDEX idx_reversal_requests_conversation ON reversal_requests (conversation_id);
CREATE INDEX idx_reversal_requests_transaction ON reversal_requests (transaction_id);

-- The original payment remembers it was reversed, and by which M-Pesa receipt.
ALTER TABLE mpesa_incoming_payments ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMPTZ;
ALTER TABLE mpesa_incoming_payments ADD COLUMN IF NOT EXISTS reversal_transaction_id VARCHAR(50);

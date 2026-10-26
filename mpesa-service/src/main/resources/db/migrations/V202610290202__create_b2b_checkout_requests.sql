-- B2B Express Checkout: USSD pushes asking a merchant's till to pay one of our paybills, matched to the
-- callback by request_ref_id (our RequestRefID, Safaricom's requestId).
CREATE TABLE b2b_checkout_requests
(
    id                    BIGSERIAL PRIMARY KEY,
    uuid                  UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid        UUID           NOT NULL REFERENCES mpesa_shortcodes (uuid),
    request_ref_id        UUID           NOT NULL UNIQUE,
    primary_short_code    VARCHAR(20)    NOT NULL,
    receiver_short_code   VARCHAR(20)    NOT NULL,
    amount                NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    payment_ref           VARCHAR(50)    NOT NULL,
    partner_name          VARCHAR(100)   NOT NULL,
    status                VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED')),
    outcome_unknown       BOOLEAN        NOT NULL DEFAULT false,
    result_code           VARCHAR(20),
    result_desc           VARCHAR(500),
    transaction_id        VARCHAR(50),
    conversation_id       VARCHAR(100),
    incoming_payment_uuid UUID,
    raw_result            JSONB,
    completed_at          TIMESTAMP,
    created_at            TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    created_by            VARCHAR(255),
    updated_by            VARCHAR(255),
    deleted_at            TIMESTAMP
);

CREATE INDEX idx_b2b_checkout_requests_shortcode_created ON b2b_checkout_requests (shortcode_uuid, created_at DESC, id DESC);
CREATE INDEX idx_b2b_checkout_requests_shortcode_amount ON b2b_checkout_requests (shortcode_uuid, amount, id DESC);

-- A settled checkout is an incoming payment of its own type.
ALTER TABLE mpesa_incoming_payments DROP CONSTRAINT mpesa_incoming_payments_payment_type_check;
ALTER TABLE mpesa_incoming_payments ADD CONSTRAINT mpesa_incoming_payments_payment_type_check
    CHECK (payment_type IN ('STK_PUSH', 'C2B', 'B2B_EXPRESS_CHECKOUT'));

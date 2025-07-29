CREATE TABLE mpesa_incoming_payments
(
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid      UUID           NOT NULL REFERENCES mpesa_shortcodes (uuid),

    payment_type        VARCHAR(20)    NOT NULL CHECK (payment_type IN ('STK_PUSH', 'C2B')),
    transaction_id      VARCHAR(50)    NOT NULL UNIQUE,
    checkout_request_id VARCHAR(50),
    merchant_request_id VARCHAR(50),

    phone_number        VARCHAR(15)    NOT NULL,
    amount              DECIMAL(10, 2) NOT NULL,
    account_reference   VARCHAR(100),
    transaction_desc    VARCHAR(255),

    result_code         INTEGER,
    result_desc         VARCHAR(500),

    first_name          VARCHAR(100),
    middle_name         VARCHAR(100),
    last_name           VARCHAR(100),

    transaction_date    TIMESTAMP,
    status              VARCHAR(20)    NOT NULL        DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED')),

    raw_callback_data   JSONB,

    created_at          TIMESTAMP                      DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP                      DEFAULT CURRENT_TIMESTAMP,
    processed_at        TIMESTAMP,
    deleted_at          TIMESTAMP
);

CREATE INDEX idx_mpesa_incoming_payments_uuid ON mpesa_incoming_payments (uuid);
CREATE INDEX idx_mpesa_incoming_payments_shortcode_uuid ON mpesa_incoming_payments (shortcode_uuid);
CREATE INDEX idx_mpesa_incoming_payments_payment_type ON mpesa_incoming_payments (payment_type);
CREATE INDEX idx_mpesa_incoming_payments_transaction_id ON mpesa_incoming_payments (transaction_id);
CREATE INDEX idx_mpesa_incoming_payments_phone_number ON mpesa_incoming_payments (phone_number);
CREATE INDEX idx_mpesa_incoming_payments_status ON mpesa_incoming_payments (status);
CREATE INDEX idx_mpesa_incoming_payments_transaction_date ON mpesa_incoming_payments (transaction_date);
CREATE INDEX idx_mpesa_incoming_payments_created_at ON mpesa_incoming_payments (created_at);

ALTER TABLE mpesa_incoming_payments
    ADD CONSTRAINT chk_amount_positive
        CHECK (amount > 0);
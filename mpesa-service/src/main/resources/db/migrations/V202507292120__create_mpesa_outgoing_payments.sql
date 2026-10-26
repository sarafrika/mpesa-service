CREATE TABLE mpesa_outgoing_payments (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid UUID NOT NULL REFERENCES mpesa_shortcodes(uuid),

    payment_type VARCHAR(20) NOT NULL CHECK (payment_type IN ('B2C', 'B2B')),
    transaction_id VARCHAR(50) UNIQUE,
    conversation_id VARCHAR(50),
    originator_conversation_id VARCHAR(50) NOT NULL UNIQUE,

    recipient_phone_number VARCHAR(15) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    command_id VARCHAR(50) NOT NULL,

    initiator_name VARCHAR(100) NOT NULL,
    remarks VARCHAR(255),
    occasion VARCHAR(255),

    result_code INTEGER,
    result_desc VARCHAR(500),

    recipient_name VARCHAR(255),
    utility_account_available_funds DECIMAL(10,2),
    working_account_available_funds DECIMAL(10,2),

    transaction_date TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED')),

    raw_callback_data JSONB,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX idx_mpesa_outgoing_payments_uuid ON mpesa_outgoing_payments (uuid);
CREATE INDEX idx_mpesa_outgoing_payments_shortcode_uuid ON mpesa_outgoing_payments (shortcode_uuid);
CREATE INDEX idx_mpesa_outgoing_payments_payment_type ON mpesa_outgoing_payments (payment_type);
CREATE INDEX idx_mpesa_outgoing_payments_transaction_id ON mpesa_outgoing_payments (transaction_id);
CREATE INDEX idx_mpesa_outgoing_payments_originator_conversation_id ON mpesa_outgoing_payments (originator_conversation_id);
CREATE INDEX idx_mpesa_outgoing_payments_recipient_phone_number ON mpesa_outgoing_payments (recipient_phone_number);
CREATE INDEX idx_mpesa_outgoing_payments_status ON mpesa_outgoing_payments (status);
CREATE INDEX idx_mpesa_outgoing_payments_transaction_date ON mpesa_outgoing_payments (transaction_date);
CREATE INDEX idx_mpesa_outgoing_payments_created_at ON mpesa_outgoing_payments (created_at);

ALTER TABLE mpesa_outgoing_payments
ADD CONSTRAINT chk_amount_positive
CHECK (amount > 0);
-- C2B confirmations are stored even for a shortcode we do not know (the money has already moved),
-- so the shortcode link becomes optional and the raw BusinessShortCode is kept beside it.
-- Daraja may send a masked or hashed MSISDN, which is longer than a plain 2547XXXXXXXX number.
ALTER TABLE mpesa_incoming_payments ALTER COLUMN shortcode_uuid DROP NOT NULL;
ALTER TABLE mpesa_incoming_payments ALTER COLUMN phone_number DROP NOT NULL;
ALTER TABLE mpesa_incoming_payments ALTER COLUMN phone_number TYPE VARCHAR(100);

ALTER TABLE mpesa_incoming_payments ADD COLUMN business_short_code VARCHAR(20);
ALTER TABLE mpesa_incoming_payments ADD COLUMN c2b_channel VARCHAR(20)
    CHECK (c2b_channel IN ('PAYBILL', 'BUY_GOODS'));
ALTER TABLE mpesa_incoming_payments ADD COLUMN invoice_number VARCHAR(100);
ALTER TABLE mpesa_incoming_payments ADD COLUMN org_account_balance DECIMAL(14, 2);
-- The Keycloak client (azp) that asked for an STK push, so its events reach the right product.
ALTER TABLE mpesa_incoming_payments ADD COLUMN client_id VARCHAR(100);

CREATE INDEX idx_mpesa_incoming_payments_checkout_request_id ON mpesa_incoming_payments (checkout_request_id);
CREATE INDEX idx_mpesa_incoming_payments_account_reference
    ON mpesa_incoming_payments (lower(account_reference) text_pattern_ops);
CREATE INDEX idx_mpesa_incoming_payments_amount ON mpesa_incoming_payments (amount);
CREATE INDEX idx_mpesa_incoming_payments_business_short_code ON mpesa_incoming_payments (business_short_code);
-- The STK status job looks only at old pending STK requests.
CREATE INDEX idx_mpesa_incoming_payments_pending_stk ON mpesa_incoming_payments (created_at)
    WHERE payment_type = 'STK_PUSH' AND status = 'PENDING' AND deleted_at IS NULL;

CREATE INDEX idx_mpesa_outgoing_payments_conversation_id ON mpesa_outgoing_payments (conversation_id);
CREATE INDEX idx_mpesa_outgoing_payments_client_reference
    ON mpesa_outgoing_payments (lower(client_reference) text_pattern_ops);
CREATE INDEX idx_mpesa_outgoing_payments_amount ON mpesa_outgoing_payments (amount);

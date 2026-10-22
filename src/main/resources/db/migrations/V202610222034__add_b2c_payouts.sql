-- B2C payouts requested by other Sarafrika services. A shortcode carries its B2C initiator and where
-- Safaricom posts B2C results; a payout carries the calling client and its own reference, so the
-- same request twice returns the first payout instead of paying again.
ALTER TABLE mpesa_shortcodes ADD COLUMN IF NOT EXISTS initiator_name VARCHAR(100);
ALTER TABLE mpesa_shortcodes ADD COLUMN IF NOT EXISTS initiator_password VARCHAR(255);
ALTER TABLE mpesa_shortcodes ADD COLUMN IF NOT EXISTS security_credential TEXT;
ALTER TABLE mpesa_shortcodes ADD COLUMN IF NOT EXISTS b2c_result_url VARCHAR(500);
ALTER TABLE mpesa_shortcodes ADD COLUMN IF NOT EXISTS b2c_timeout_url VARCHAR(500);

ALTER TABLE mpesa_outgoing_payments ADD COLUMN IF NOT EXISTS client_id VARCHAR(100);
ALTER TABLE mpesa_outgoing_payments ADD COLUMN IF NOT EXISTS client_reference VARCHAR(100);
ALTER TABLE mpesa_outgoing_payments ADD COLUMN IF NOT EXISTS charges_paid DECIMAL(10, 2);
ALTER TABLE mpesa_outgoing_payments ADD COLUMN IF NOT EXISTS timed_out_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS uq_mpesa_outgoing_payments_client_reference
    ON mpesa_outgoing_payments (client_id, client_reference)
    WHERE client_reference IS NOT NULL AND deleted_at IS NULL;

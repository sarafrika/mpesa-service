-- A payout Safaricom never answered (timeout, network, 5xx) stays PENDING but is flagged: it may still be paid.
ALTER TABLE mpesa_outgoing_payments ADD COLUMN outcome_unknown BOOLEAN NOT NULL DEFAULT false;
UPDATE mpesa_outgoing_payments SET outcome_unknown = true WHERE status = 'PENDING' AND timed_out_at IS NOT NULL;

-- The STK status job asks again with a growing delay instead of re-reading the same oldest rows forever.
ALTER TABLE mpesa_incoming_payments ADD COLUMN next_check_at TIMESTAMP;
ALTER TABLE mpesa_incoming_payments ADD COLUMN status_checks INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_mpesa_incoming_payments_stk_next_check ON mpesa_incoming_payments (next_check_at, created_at)
    WHERE payment_type = 'STK_PUSH' AND status = 'PENDING' AND deleted_at IS NULL;

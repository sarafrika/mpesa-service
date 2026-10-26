-- Finance may close a sweep or reversal whose outcome stayed unknown as FAILED once the statement shows nothing
-- moved; who did it and their note are kept. A Safaricom result arriving later still settles it.
ALTER TABLE b2b_transfers ADD COLUMN closed_by VARCHAR(100);
ALTER TABLE b2b_transfers ADD COLUMN close_note VARCHAR(500);

ALTER TABLE reversal_requests ADD COLUMN closed_by VARCHAR(100);
ALTER TABLE reversal_requests ADD COLUMN close_note VARCHAR(500);

-- The fallback match of a B2B result that carries no ids we stored: open transfers by receiver and amount.
CREATE INDEX idx_b2b_transfers_unmatched ON b2b_transfers (receiver_shortcode, amount, created_at)
    WHERE status = 'PENDING' AND conversation_id IS NULL AND originator_conversation_id IS NULL;

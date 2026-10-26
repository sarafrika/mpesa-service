-- A live C2B confirmation that several receiptless STK payments may be: stored as before, but counted here so
-- the Overview flags it for reconciliation instead of the money silently counting twice.
ALTER TABLE mpesa_incoming_payments ADD COLUMN possible_stk_duplicates INTEGER;

CREATE INDEX idx_mpesa_incoming_payments_possible_stk_duplicates ON mpesa_incoming_payments (shortcode_uuid, created_at)
    WHERE possible_stk_duplicates IS NOT NULL AND deleted_at IS NULL;

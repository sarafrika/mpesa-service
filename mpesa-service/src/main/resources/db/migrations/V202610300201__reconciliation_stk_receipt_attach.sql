-- An STK payment settled by the STK query has no receipt (the query reply carries none), so a statement line
-- for it must be attached to that payment instead of backfilled as a second deposit.
ALTER TABLE reconciliation_exceptions DROP CONSTRAINT reconciliation_exceptions_last_action_check;
ALTER TABLE reconciliation_exceptions ADD CONSTRAINT reconciliation_exceptions_last_action_check
    CHECK (last_action IN ('BACKFILL', 'ATTACH_STK_RECEIPT', 'QUERY_STATUS', 'MERGE_DUPLICATE', 'MARK_RESOLVED'));
ALTER TABLE reconciliation_exceptions ADD COLUMN stk_candidates INTEGER NOT NULL DEFAULT 0;

-- Settled STK payments still holding their checkout id: the candidates a statement receipt may belong to.
CREATE INDEX idx_mpesa_incoming_payments_receiptless_stk ON mpesa_incoming_payments (shortcode_uuid, amount, created_at)
    WHERE payment_type = 'STK_PUSH' AND status = 'SUCCESS' AND deleted_at IS NULL
      AND transaction_id = checkout_request_id;

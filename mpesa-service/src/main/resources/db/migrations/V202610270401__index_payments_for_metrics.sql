-- Overview metrics read payments by shortcode and time, pending STK requests by age, and each
-- shortcode's newest float report; these indexes keep those aggregates off full scans.
-- incoming (shortcode_uuid, created_at) is covered by idx_mpesa_incoming_payments_shortcode_created.
CREATE INDEX IF NOT EXISTS idx_metrics_outgoing_shortcode_created
    ON mpesa_outgoing_payments (shortcode_uuid, created_at) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_metrics_incoming_shortcode_processed
    ON mpesa_incoming_payments (shortcode_uuid, processed_at) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_metrics_outgoing_shortcode_processed
    ON mpesa_outgoing_payments (shortcode_uuid, processed_at) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_metrics_incoming_status_created
    ON mpesa_incoming_payments (status, created_at) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_metrics_outgoing_status_created
    ON mpesa_outgoing_payments (status, created_at) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_metrics_outgoing_latest_float
    ON mpesa_outgoing_payments (shortcode_uuid, processed_at DESC)
    WHERE utility_account_available_funds IS NOT NULL AND deleted_at IS NULL;

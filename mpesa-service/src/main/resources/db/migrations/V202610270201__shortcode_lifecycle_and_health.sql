-- Shortcodes gain a B2C disbursement kind, a lifecycle (draft until first activated, then active or
-- suspended), when their credentials were last rotated, and the outcome of the last Daraja token check.
ALTER TABLE mpesa_shortcodes DROP CONSTRAINT IF EXISTS mpesa_shortcodes_shortcode_type_check;
ALTER TABLE mpesa_shortcodes
    ADD CONSTRAINT mpesa_shortcodes_shortcode_type_check CHECK (shortcode_type IN ('PAYBILL', 'TILL', 'B2C'));

ALTER TABLE mpesa_shortcodes ADD COLUMN activated_at TIMESTAMP;
ALTER TABLE mpesa_shortcodes ADD COLUMN suspended_at TIMESTAMP;
ALTER TABLE mpesa_shortcodes ADD COLUMN credentials_rotated_at TIMESTAMP;
ALTER TABLE mpesa_shortcodes ADD COLUMN last_connection_ok BOOLEAN;
ALTER TABLE mpesa_shortcodes ADD COLUMN last_connection_checked_at TIMESTAMP;
ALTER TABLE mpesa_shortcodes ADD COLUMN last_connection_latency_ms INTEGER;
ALTER TABLE mpesa_shortcodes ADD COLUMN last_connection_error VARCHAR(500);

-- Existing rows: active ones count as activated when created; credentials date from creation.
UPDATE mpesa_shortcodes SET activated_at = created_at WHERE is_active AND activated_at IS NULL;
UPDATE mpesa_shortcodes SET suspended_at = updated_at WHERE NOT is_active AND suspended_at IS NULL;
UPDATE mpesa_shortcodes SET credentials_rotated_at = COALESCE(created_at, NOW()) WHERE credentials_rotated_at IS NULL;

-- The console sorts by name and searches it; shortcode and created_at are already indexed.
CREATE INDEX IF NOT EXISTS idx_mpesa_shortcodes_business_name ON mpesa_shortcodes (business_name);
CREATE INDEX IF NOT EXISTS idx_mpesa_shortcodes_lower_business_name ON mpesa_shortcodes (LOWER(business_name));
-- The list's health columns read each shortcode's newest payments.
CREATE INDEX IF NOT EXISTS idx_mpesa_incoming_payments_shortcode_created
    ON mpesa_incoming_payments (shortcode_uuid, created_at);

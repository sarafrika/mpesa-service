-- BaseEntity declares created_by / updated_by, but the original create migrations
-- omitted them (only surfaced against a real Postgres with ddl-auto=validate).
-- Add them to all mpesa tables. IF NOT EXISTS keeps this safe on partially-migrated DBs.
ALTER TABLE mpesa_shortcodes        ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE mpesa_shortcodes        ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
ALTER TABLE mpesa_incoming_payments ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE mpesa_incoming_payments ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
ALTER TABLE mpesa_outgoing_payments ADD COLUMN IF NOT EXISTS created_by VARCHAR(255);
ALTER TABLE mpesa_outgoing_payments ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

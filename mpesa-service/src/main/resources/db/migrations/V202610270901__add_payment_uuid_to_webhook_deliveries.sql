-- Lets a transaction's timeline list the webhooks sent about it without scanning payloads.
ALTER TABLE webhook_deliveries ADD COLUMN payment_uuid UUID;

UPDATE webhook_deliveries
SET payment_uuid = (payload ->> 'payment_uuid')::uuid
WHERE payload ->> 'payment_uuid' IS NOT NULL;

CREATE INDEX idx_webhook_deliveries_payment ON webhook_deliveries (payment_uuid, created_at) WHERE payment_uuid IS NOT NULL;

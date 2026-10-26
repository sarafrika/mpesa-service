-- Each claim of a delivery gets a fresh token; an attempt records its outcome only while it still holds it,
-- so a worker that outlived its lease cannot overwrite the attempt that replaced it.
ALTER TABLE webhook_deliveries ADD COLUMN claim_token UUID;

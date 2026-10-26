-- Daraja has no rotation API: the console only stores credentials after they were changed at Safaricom.
ALTER TABLE mpesa_shortcodes RENAME COLUMN credentials_rotated_at TO credentials_updated_at;

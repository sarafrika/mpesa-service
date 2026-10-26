-- Shortcode secrets are now stored as enc:v1:<base64(iv|ciphertext|tag)>, about 4/3 of the plaintext
-- plus 44 characters, which a full-length value in the old VARCHAR limits would overflow.
ALTER TABLE mpesa_shortcodes ALTER COLUMN consumer_key TYPE TEXT;
ALTER TABLE mpesa_shortcodes ALTER COLUMN consumer_secret TYPE TEXT;
ALTER TABLE mpesa_shortcodes ALTER COLUMN passkey TYPE TEXT;
ALTER TABLE mpesa_shortcodes ALTER COLUMN initiator_password TYPE TEXT;

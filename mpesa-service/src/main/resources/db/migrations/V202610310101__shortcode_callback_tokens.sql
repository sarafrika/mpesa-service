-- A random secret per shortcode, used as a path segment in the callback URLs given to Safaricom.
ALTER TABLE mpesa_shortcodes ADD COLUMN callback_token VARCHAR(64);

UPDATE mpesa_shortcodes
SET callback_token = replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '')
WHERE callback_token IS NULL;

ALTER TABLE mpesa_shortcodes
    ALTER COLUMN callback_token SET NOT NULL,
    ALTER COLUMN callback_token SET DEFAULT replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');

CREATE UNIQUE INDEX ux_mpesa_shortcodes_callback_token ON mpesa_shortcodes (callback_token);

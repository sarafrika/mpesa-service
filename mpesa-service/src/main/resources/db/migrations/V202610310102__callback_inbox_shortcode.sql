-- The shortcode whose own callback URL (token path) a callback arrived on; null on the shared paths.
ALTER TABLE callback_inbox ADD COLUMN shortcode_uuid UUID;

CREATE INDEX idx_callback_inbox_shortcode_failures ON callback_inbox (shortcode_uuid, received_at DESC)
    WHERE status = 'FAILED';

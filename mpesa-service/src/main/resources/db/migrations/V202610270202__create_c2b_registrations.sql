-- Every attempt to register a shortcode's C2B URLs with Safaricom, successful or not.
-- The newest row per shortcode is its registration state on the console.
CREATE TABLE c2b_registrations
(
    id               BIGSERIAL PRIMARY KEY,
    uuid             UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid   UUID          NOT NULL REFERENCES mpesa_shortcodes (uuid),
    response_type    VARCHAR(20)   NOT NULL CHECK (response_type IN ('Completed', 'Cancelled')),
    confirmation_url VARCHAR(500)  NOT NULL,
    validation_url   VARCHAR(500)  NOT NULL,
    successful       BOOLEAN       NOT NULL,
    result_code      VARCHAR(50),
    result_desc      VARCHAR(1000),
    raw              JSONB,
    registered_by    VARCHAR(255)  NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_c2b_registrations_shortcode_created ON c2b_registrations (shortcode_uuid, created_at DESC);

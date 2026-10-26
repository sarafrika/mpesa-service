-- Which Keycloak clients (products, by azp) may use which shortcode, and for which operations.
-- operations is a comma-separated set of STK_PUSH, C2B_FEED, B2C_PAYOUT and STATUS_LOOKUP.
CREATE TABLE client_shortcode_grants
(
    id             BIGSERIAL PRIMARY KEY,
    uuid           UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    client_id      VARCHAR(100) NOT NULL,
    display_name   VARCHAR(255),
    shortcode_uuid UUID         NOT NULL REFERENCES mpesa_shortcodes (uuid),
    operations     VARCHAR(100) NOT NULL        DEFAULT '',
    active         BOOLEAN      NOT NULL        DEFAULT true,
    created_at     TIMESTAMP                    DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP                    DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_by     VARCHAR(100),
    deleted_at     TIMESTAMP,
    CONSTRAINT chk_client_shortcode_grants_operations CHECK (operations ~
        '^((STK_PUSH|C2B_FEED|B2C_PAYOUT|STATUS_LOOKUP)(,(STK_PUSH|C2B_FEED|B2C_PAYOUT|STATUS_LOOKUP))*)?$')
);

CREATE UNIQUE INDEX uq_client_shortcode_grants_client_shortcode
    ON client_shortcode_grants (client_id, shortcode_uuid) WHERE deleted_at IS NULL;
CREATE INDEX idx_client_shortcode_grants_shortcode
    ON client_shortcode_grants (shortcode_uuid) WHERE deleted_at IS NULL;
CREATE INDEX idx_client_shortcode_grants_created_at ON client_shortcode_grants (created_at);

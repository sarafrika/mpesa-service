-- How a shortcode answers Safaricom's C2B validation request; no row (or an inactive one) accepts everything.
CREATE TABLE c2b_validation_rules
(
    id                BIGSERIAL PRIMARY KEY,
    uuid              UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid    UUID        NOT NULL REFERENCES mpesa_shortcodes (uuid),
    account_pattern   VARCHAR(255),
    min_amount        DECIMAL(12, 2) CHECK (min_amount IS NULL OR min_amount > 0),
    max_amount        DECIMAL(12, 2) CHECK (max_amount IS NULL OR max_amount > 0),
    lookup_url        VARCHAR(500),
    lookup_timeout_ms INTEGER     NOT NULL DEFAULT 2000 CHECK (lookup_timeout_ms BETWEEN 100 AND 5000),
    on_lookup_failure VARCHAR(10) NOT NULL DEFAULT 'REJECT' CHECK (on_lookup_failure IN ('ACCEPT', 'REJECT')),
    active            BOOLEAN     NOT NULL DEFAULT true,
    created_at        TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    deleted_at        TIMESTAMP,
    CONSTRAINT chk_c2b_validation_rules_range
        CHECK (min_amount IS NULL OR max_amount IS NULL OR min_amount <= max_amount)
);

CREATE UNIQUE INDEX uq_c2b_validation_rules_shortcode ON c2b_validation_rules (shortcode_uuid) WHERE deleted_at IS NULL;

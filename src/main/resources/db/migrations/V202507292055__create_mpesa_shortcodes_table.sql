CREATE TABLE mpesa_shortcodes
(
    id                BIGSERIAL PRIMARY KEY,
    uuid              UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode         VARCHAR(10)  NOT NULL UNIQUE,
    shortcode_type    VARCHAR(20)  NOT NULL CHECK (shortcode_type IN ('PAYBILL', 'TILL')),
    business_name     VARCHAR(255) NOT NULL,

    consumer_key      VARCHAR(255) NOT NULL,
    consumer_secret   VARCHAR(500) NOT NULL,
    passkey           VARCHAR(500),

    callback_url      VARCHAR(500) NOT NULL,
    confirmation_url  VARCHAR(500),
    validation_url    VARCHAR(500),

    min_amount        DECIMAL(10, 2)               DEFAULT 1.00,
    max_amount        DECIMAL(10, 2)               DEFAULT 70000.00,

    is_active         BOOLEAN      NOT NULL        DEFAULT true,
    environment       VARCHAR(20)  NOT NULL        DEFAULT 'SANDBOX' CHECK (environment IN ('SANDBOX', 'PRODUCTION')),

    account_reference VARCHAR(100),
    transaction_desc  VARCHAR(255)                 DEFAULT 'Payment',

    created_at        TIMESTAMP                    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP                    DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(100),
    updated_by        VARCHAR(100),
    deleted_at        TIMESTAMP
);

CREATE INDEX idx_mpesa_shortcodes_uuid ON mpesa_shortcodes (uuid);
CREATE INDEX idx_mpesa_shortcodes_shortcode_type ON mpesa_shortcodes (shortcode_type);
CREATE INDEX idx_mpesa_shortcodes_environment ON mpesa_shortcodes (environment);
CREATE INDEX idx_mpesa_shortcodes_is_active ON mpesa_shortcodes (is_active);
CREATE INDEX idx_mpesa_shortcodes_created_at ON mpesa_shortcodes (created_at);

ALTER TABLE mpesa_shortcodes
    ADD CONSTRAINT chk_amount_range
        CHECK (min_amount >= 0 AND max_amount > min_amount);
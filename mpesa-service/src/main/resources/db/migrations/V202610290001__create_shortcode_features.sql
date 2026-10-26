-- Per-shortcode switches for Daraja products Safaricom enables separately on each app; all off by default.
CREATE TABLE shortcode_features
(
    id             BIGSERIAL PRIMARY KEY,
    shortcode_uuid UUID        NOT NULL REFERENCES mpesa_shortcodes (uuid),
    feature        VARCHAR(40) NOT NULL CHECK (feature IN ('PULL_TRANSACTIONS', 'TAX_REMITTANCE', 'BUSINESS_TO_POCHI',
                                                           'B2B_EXPRESS_CHECKOUT', 'RATIBA')),
    enabled        BOOLEAN     NOT NULL DEFAULT false,
    updated_by     VARCHAR(255),
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_shortcode_features UNIQUE (shortcode_uuid, feature)
);

-- Dynamic QR codes generated through Daraja for a shortcode: the request sent and the PNG returned.
CREATE TABLE qr_codes
(
    id             BIGSERIAL PRIMARY KEY,
    uuid           UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid UUID          NOT NULL REFERENCES mpesa_shortcodes (uuid),
    merchant_name  VARCHAR(100)  NOT NULL,
    ref_no         VARCHAR(100)  NOT NULL,
    amount         NUMERIC(15, 2),
    trx_code       VARCHAR(2)    NOT NULL,
    cpi            VARCHAR(20)   NOT NULL,
    size           INTEGER       NOT NULL,
    request_id     VARCHAR(100),
    image_base64   TEXT          NOT NULL,
    created_by     VARCHAR(100),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qr_codes_shortcode ON qr_codes (shortcode_uuid, created_at DESC, id DESC);

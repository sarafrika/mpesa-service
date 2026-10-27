-- One Account (paybill and B2C on one shortcode) joins the shortcode types.
ALTER TABLE mpesa_shortcodes DROP CONSTRAINT IF EXISTS mpesa_shortcodes_shortcode_type_check;
ALTER TABLE mpesa_shortcodes
    ADD CONSTRAINT mpesa_shortcodes_shortcode_type_check
        CHECK (shortcode_type IN ('PAYBILL', 'TILL', 'B2C', 'ONE_ACCOUNT'));

-- A till's store/HO number (the Go-Live shortcode); the shortcode column keeps the till number.
ALTER TABLE mpesa_shortcodes ADD COLUMN store_number VARCHAR(20);
ALTER TABLE mpesa_shortcodes ADD COLUMN store_number_confirmed_at TIMESTAMP;
CREATE INDEX idx_mpesa_shortcodes_store_number ON mpesa_shortcodes (store_number) WHERE store_number IS NOT NULL;

-- Tills used the till number everywhere until now; the console asks someone to confirm it.
UPDATE mpesa_shortcodes SET store_number = shortcode WHERE shortcode_type = 'TILL' AND store_number IS NULL;

-- Paybills that already paid out (B2C or Pochi) keep working as One Accounts.
UPDATE mpesa_shortcodes s SET shortcode_type = 'ONE_ACCOUNT'
WHERE s.shortcode_type = 'PAYBILL'
  AND (EXISTS (SELECT 1 FROM mpesa_outgoing_payments p WHERE p.shortcode_uuid = s.uuid AND p.payment_type = 'B2C')
    OR EXISTS (SELECT 1 FROM product_payouts p WHERE p.shortcode_uuid = s.uuid AND p.product = 'BUSINESS_TO_POCHI'));

-- Every non-till keeps no store number.
UPDATE mpesa_shortcodes SET store_number = NULL WHERE shortcode_type <> 'TILL';

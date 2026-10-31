-- What each shortcode is set up to do; its credentials follow these operations, not its type.
-- A comma-separated set in enum order. NULL (rows written outside the service) means all the type allows.
ALTER TABLE mpesa_shortcodes ADD COLUMN operations VARCHAR(200);
ALTER TABLE mpesa_shortcodes
    ADD CONSTRAINT chk_mpesa_shortcodes_operations CHECK (operations ~
        '^((C2B|STK_PUSH|REVERSAL|TRANSACTION_STATUS|ACCOUNT_BALANCE|B2C_PAYOUT|POCHI)(,(C2B|STK_PUSH|REVERSAL|TRANSACTION_STATUS|ACCOUNT_BALANCE|B2C_PAYOUT|POCHI))*)?$');

-- Existing shortcodes keep everything their type allows, so nothing that works today stops.
UPDATE mpesa_shortcodes SET operations = CASE shortcode_type
    WHEN 'B2C' THEN 'TRANSACTION_STATUS,ACCOUNT_BALANCE,B2C_PAYOUT,POCHI'
    WHEN 'ONE_ACCOUNT' THEN 'C2B,STK_PUSH,REVERSAL,TRANSACTION_STATUS,ACCOUNT_BALANCE,B2C_PAYOUT,POCHI'
    ELSE 'C2B,STK_PUSH,REVERSAL,TRANSACTION_STATUS,ACCOUNT_BALANCE'
END;

-- Safaricom expires an initiator password after 90 days; this is when the stored one was saved.
ALTER TABLE mpesa_shortcodes ADD COLUMN initiator_credential_set_at TIMESTAMP;
UPDATE mpesa_shortcodes SET initiator_credential_set_at = updated_at
WHERE COALESCE(initiator_password, '') <> '' OR COALESCE(security_credential, '') <> '';

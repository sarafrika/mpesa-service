-- B2C Account Top Up (developer.safaricom.co.ke/apis/B2CAccountTopUp): a destination may be one of our own
-- B2C shortcodes, paid with CommandID BusinessPayToBulk on the same B2B endpoint.
ALTER TABLE b2b_destinations ALTER COLUMN receiver_type TYPE VARCHAR(20);
ALTER TABLE b2b_destinations DROP CONSTRAINT b2b_destinations_receiver_type_check;
ALTER TABLE b2b_destinations ADD CONSTRAINT b2b_destinations_receiver_type_check
    CHECK (receiver_type IN ('PAYBILL', 'TILL', 'B2C_SHORTCODE'));

ALTER TABLE b2b_transfers DROP CONSTRAINT b2b_transfers_command_id_check;
ALTER TABLE b2b_transfers ADD CONSTRAINT b2b_transfers_command_id_check
    CHECK (command_id IN ('BusinessPayBill', 'BusinessBuyGoods', 'BusinessPayToBulk'));

-- What the result reports: DebitPartyCharges, TransCompletedTime and the Working account's available balance
-- from DebitPartyAffectedAccountBalance, so a sweep's result is also a Working balance reading.
ALTER TABLE b2b_transfers
    ADD COLUMN charges               NUMERIC(15, 2),
    ADD COLUMN trans_completed_at    TIMESTAMPTZ,
    ADD COLUMN working_balance_after NUMERIC(15, 2);

CREATE INDEX idx_b2b_transfers_shortcode_working ON b2b_transfers (shortcode_uuid)
    WHERE working_balance_after IS NOT NULL AND status = 'SUCCESS' AND deleted_at IS NULL;

-- A balance query sent by itself after a successful sweep or top-up.
ALTER TABLE balance_snapshots DROP CONSTRAINT balance_snapshots_source_check;
ALTER TABLE balance_snapshots ADD CONSTRAINT balance_snapshots_source_check
    CHECK (source IN ('MANUAL', 'SCHEDULED', 'AFTER_TRANSFER'));

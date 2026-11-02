-- Every TIMESTAMP column becomes TIMESTAMPTZ. Values written by the service are UTC wall time (the JVM runs in
-- UTC); Safaricom's times (TransTime, STK TransactionDate, B2C TransactionCompletedDateTime, statement and
-- Pull Transactions times, and reconciliation periods) are East Africa Time. Nothing here reads the session TimeZone.
DROP VIEW mpesa_transaction_feed;

-- All writers store Nairobi wall time, except B2B Express Checkout rows written briefly with UTC now(): those sit
-- next to created_at (UTC) rather than about three hours after it.
ALTER TABLE mpesa_incoming_payments
    ALTER COLUMN transaction_date TYPE TIMESTAMPTZ USING CASE
        WHEN payment_type = 'B2B_EXPRESS_CHECKOUT'
            AND abs(extract(EPOCH FROM transaction_date - created_at)) < 5400
            THEN transaction_date AT TIME ZONE 'UTC'
        ELSE transaction_date AT TIME ZONE 'Africa/Nairobi' END;

ALTER TABLE mpesa_outgoing_payments
    ALTER COLUMN transaction_date TYPE TIMESTAMPTZ USING transaction_date AT TIME ZONE 'Africa/Nairobi';

ALTER TABLE reconciliation_exceptions
    ALTER COLUMN statement_time TYPE TIMESTAMPTZ USING statement_time AT TIME ZONE 'Africa/Nairobi';

ALTER TABLE reconciliation_runs
    ALTER COLUMN period_start TYPE TIMESTAMPTZ USING period_start AT TIME ZONE 'Africa/Nairobi',
    ALTER COLUMN period_end TYPE TIMESTAMPTZ USING period_end AT TIME ZONE 'Africa/Nairobi';

-- The rest are the service's own UTC stamps.
ALTER TABLE mpesa_incoming_payments
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN processed_at TYPE TIMESTAMPTZ USING processed_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN next_check_at TYPE TIMESTAMPTZ USING next_check_at AT TIME ZONE 'UTC';

ALTER TABLE mpesa_outgoing_payments
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN processed_at TYPE TIMESTAMPTZ USING processed_at AT TIME ZONE 'UTC',
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN timed_out_at TYPE TIMESTAMPTZ USING timed_out_at AT TIME ZONE 'UTC';

ALTER TABLE mpesa_shortcodes
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN activated_at TYPE TIMESTAMPTZ USING activated_at AT TIME ZONE 'UTC',
    ALTER COLUMN suspended_at TYPE TIMESTAMPTZ USING suspended_at AT TIME ZONE 'UTC',
    ALTER COLUMN credentials_updated_at TYPE TIMESTAMPTZ USING credentials_updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_connection_checked_at TYPE TIMESTAMPTZ USING last_connection_checked_at AT TIME ZONE 'UTC',
    ALTER COLUMN next_balance_query_at TYPE TIMESTAMPTZ USING next_balance_query_at AT TIME ZONE 'UTC',
    ALTER COLUMN store_number_confirmed_at TYPE TIMESTAMPTZ USING store_number_confirmed_at AT TIME ZONE 'UTC',
    ALTER COLUMN initiator_credential_set_at TYPE TIMESTAMPTZ USING initiator_credential_set_at AT TIME ZONE 'UTC';

ALTER TABLE b2b_checkout_requests
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC';

ALTER TABLE b2b_destinations
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

ALTER TABLE b2b_transfers
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN timed_out_at TYPE TIMESTAMPTZ USING timed_out_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC';

ALTER TABLE balance_snapshots
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN requested_at DROP DEFAULT,
    ALTER COLUMN requested_at TYPE TIMESTAMPTZ USING requested_at AT TIME ZONE 'UTC',
    ALTER COLUMN requested_at SET DEFAULT now(),
    ALTER COLUMN received_at TYPE TIMESTAMPTZ USING received_at AT TIME ZONE 'UTC';

ALTER TABLE c2b_registrations
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now();

ALTER TABLE c2b_validation_rules
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

ALTER TABLE client_shortcode_grants
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC';

ALTER TABLE product_payouts
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN timed_out_at TYPE TIMESTAMPTZ USING timed_out_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC';

ALTER TABLE pull_transaction_registrations
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN registered_at TYPE TIMESTAMPTZ USING registered_at AT TIME ZONE 'UTC';

ALTER TABLE reconciliation_exceptions
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN resolved_at TYPE TIMESTAMPTZ USING resolved_at AT TIME ZONE 'UTC';

ALTER TABLE reconciliation_runs
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC';

ALTER TABLE shortcode_features
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now();

ALTER TABLE transaction_status_queries
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC';

ALTER TABLE webhook_subscriptions
    ALTER COLUMN created_at DROP DEFAULT,
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN created_at SET DEFAULT now(),
    ALTER COLUMN updated_at DROP DEFAULT,
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN deleted_at TYPE TIMESTAMPTZ USING deleted_at AT TIME ZONE 'UTC',
    ALTER COLUMN secret_rotated_at TYPE TIMESTAMPTZ USING secret_rotated_at AT TIME ZONE 'UTC';

-- Unchanged apart from the column types it now inherits.
CREATE VIEW mpesa_transaction_feed AS
SELECT p.uuid                                                    AS id,
       'IN'::VARCHAR(3)                                          AS direction,
       p.payment_type::VARCHAR(20)                               AS type,
       p.status::VARCHAR(20)                                     AS status,
       p.shortcode_uuid                                          AS shortcode_uuid,
       COALESCE(s.shortcode, p.business_short_code)::VARCHAR(20) AS shortcode,
       s.business_name::VARCHAR(255)                             AS business_name,
       p.transaction_id::VARCHAR(50)                             AS transaction_id,
       p.checkout_request_id::VARCHAR(100)                       AS checkout_request_id,
       NULL::VARCHAR(100)                                        AS conversation_id,
       NULL::VARCHAR(100)                                        AS originator_conversation_id,
       p.account_reference::VARCHAR(100)                         AS account_reference,
       p.phone_number::VARCHAR(100)                              AS phone,
       p.amount                                                  AS amount,
       p.result_code                                             AS result_code,
       p.result_desc::VARCHAR(500)                               AS result_desc,
       p.client_id::VARCHAR(100)                                 AS client_id,
       NULLIF(concat_ws(' ', p.first_name, p.middle_name, p.last_name), '')::VARCHAR(255) AS customer_name,
       p.c2b_channel::VARCHAR(20)                                AS c2b_channel,
       p.created_at                                              AS created_at,
       p.processed_at                                            AS processed_at,
       p.transaction_date                                        AS transaction_date
FROM mpesa_incoming_payments p
         LEFT JOIN mpesa_shortcodes s ON s.uuid = p.shortcode_uuid
WHERE p.deleted_at IS NULL
UNION ALL
SELECT o.uuid,
       'OUT'::VARCHAR(3),
       o.payment_type::VARCHAR(20),
       o.status::VARCHAR(20),
       o.shortcode_uuid,
       s.shortcode::VARCHAR(20),
       s.business_name::VARCHAR(255),
       o.transaction_id::VARCHAR(50),
       NULL::VARCHAR(100),
       o.conversation_id::VARCHAR(100),
       o.originator_conversation_id::VARCHAR(100),
       o.client_reference::VARCHAR(100),
       o.recipient_phone_number::VARCHAR(100),
       o.amount,
       o.result_code,
       o.result_desc::VARCHAR(500),
       o.client_id::VARCHAR(100),
       o.recipient_name::VARCHAR(255),
       NULL::VARCHAR(20),
       o.created_at,
       o.processed_at,
       o.transaction_date
FROM mpesa_outgoing_payments o
         LEFT JOIN mpesa_shortcodes s ON s.uuid = o.shortcode_uuid
WHERE o.deleted_at IS NULL;

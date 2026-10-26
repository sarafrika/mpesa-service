-- One feed over money in and money out for the console. UNION ALL keeps each branch on its own
-- table's indexes, so filters and ORDER BY created_at LIMIT n push down into both tables.
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

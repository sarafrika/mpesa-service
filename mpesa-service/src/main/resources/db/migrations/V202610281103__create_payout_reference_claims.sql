-- A bulk payout claims each row's reference while its batch waits or runs; the primary key lets only one
-- batch hold a reference, so two uploads at the same moment cannot both queue it. Released when the batch
-- is rejected, expires or completes (the payouts themselves then guard the reference).
CREATE TABLE payout_reference_claims
(
    reference  VARCHAR(100) PRIMARY KEY,
    batch_id   BIGINT      NOT NULL REFERENCES payout_batches (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payout_reference_claims_batch ON payout_reference_claims (batch_id);

INSERT INTO payout_reference_claims (reference, batch_id)
SELECT DISTINCT ON (i.reference) i.reference, i.batch_id
FROM payout_batch_items i
         JOIN payout_batches b ON b.id = i.batch_id
WHERE b.status IN ('WAITING', 'RUNNING')
ORDER BY i.reference, i.batch_id;

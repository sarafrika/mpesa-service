-- One reconciliation of a shortcode's ledger against Safaricom's record for a period: an uploaded
-- organisation statement or a Pull Transactions query. Counts and values are kept so the run reads alone.
CREATE TABLE reconciliation_runs
(
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid      UUID           NOT NULL REFERENCES mpesa_shortcodes (uuid),
    source              VARCHAR(30)    NOT NULL CHECK (source IN ('STATEMENT', 'PULL_TRANSACTIONS')),
    trigger_type        VARCHAR(20)    NOT NULL CHECK (trigger_type IN ('MANUAL', 'SCHEDULED')),
    status              VARCHAR(20)    NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    period_start        TIMESTAMP      NOT NULL,
    period_end          TIMESTAMP      NOT NULL,
    file_name           VARCHAR(255),
    statement_lines     INTEGER        NOT NULL DEFAULT 0,
    skipped_lines       INTEGER        NOT NULL DEFAULT 0,
    charge_lines        INTEGER        NOT NULL DEFAULT 0,
    reversal_lines      INTEGER        NOT NULL DEFAULT 0,
    ledger_records      INTEGER        NOT NULL DEFAULT 0,
    matched_count       INTEGER        NOT NULL DEFAULT 0,
    matched_value       DECIMAL(16, 2) NOT NULL DEFAULT 0,
    exception_count     INTEGER        NOT NULL DEFAULT 0,
    exception_value     DECIMAL(16, 2) NOT NULL DEFAULT 0,
    paid_in_total       DECIMAL(16, 2) NOT NULL DEFAULT 0,
    withdrawn_total     DECIMAL(16, 2) NOT NULL DEFAULT 0,
    charges_total       DECIMAL(16, 2) NOT NULL DEFAULT 0,
    failure_reason      VARCHAR(1000),
    created_by          VARCHAR(255),
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP,
    CONSTRAINT chk_reconciliation_runs_period CHECK (period_end > period_start)
);

CREATE INDEX idx_reconciliation_runs_shortcode_created ON reconciliation_runs (shortcode_uuid, created_at DESC, id DESC);
CREATE INDEX idx_reconciliation_runs_created ON reconciliation_runs (created_at DESC, id DESC);

-- A receipt the run could not match, what to do about it, and how a person resolved it.
CREATE TABLE reconciliation_exceptions
(
    id                  BIGSERIAL PRIMARY KEY,
    uuid                UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    run_id              BIGINT         NOT NULL REFERENCES reconciliation_runs (id),
    shortcode_uuid      UUID           NOT NULL REFERENCES mpesa_shortcodes (uuid),
    kind                VARCHAR(30)    NOT NULL CHECK (kind IN ('MISSING_IN_LEDGER', 'MISSING_IN_STATEMENT',
                                                                'AMOUNT_MISMATCH', 'DUPLICATE_IN_LEDGER')),
    status              VARCHAR(20)    NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'SUPERSEDED')),
    direction           VARCHAR(3)     NOT NULL CHECK (direction IN ('IN', 'OUT')),
    receipt             VARCHAR(50)    NOT NULL,
    statement_amount    DECIMAL(16, 2),
    ledger_amount       DECIMAL(16, 2),
    ledger_count        INTEGER        NOT NULL DEFAULT 0,
    ledger_payment_uuid UUID,
    ledger_status       VARCHAR(20),
    ledger_type         VARCHAR(20),
    statement_time      TIMESTAMP,
    details             VARCHAR(500),
    account_reference   VARCHAR(100),
    other_party         VARCHAR(255),
    reversed_by         VARCHAR(50),
    suggested_fix       VARCHAR(500)   NOT NULL,
    statement_line      JSONB,
    last_action         VARCHAR(30) CHECK (last_action IN ('BACKFILL', 'QUERY_STATUS', 'MERGE_DUPLICATE', 'MARK_RESOLVED')),
    status_query_uuid   UUID,
    resolution_note     VARCHAR(1000),
    resolved_by         VARCHAR(255),
    resolved_at         TIMESTAMP,
    created_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reconciliation_exceptions_run ON reconciliation_exceptions (run_id, id DESC);
CREATE INDEX idx_reconciliation_exceptions_run_kind_status ON reconciliation_exceptions (run_id, kind, status);
CREATE INDEX idx_reconciliation_exceptions_open ON reconciliation_exceptions (shortcode_uuid) WHERE status = 'OPEN';
CREATE INDEX idx_reconciliation_exceptions_receipt ON reconciliation_exceptions (receipt);
CREATE INDEX idx_reconciliation_exceptions_run_amount ON reconciliation_exceptions (run_id, statement_amount, id DESC);
CREATE INDEX idx_reconciliation_exceptions_run_receipt ON reconciliation_exceptions (run_id, receipt, id DESC);

-- A shortcode's Pull Transactions registration with Safaricom (one-time per shortcode) and the day the
-- nightly run last claimed it, so several instances never reconcile the same day twice.
CREATE TABLE pull_transaction_registrations
(
    id                   BIGSERIAL PRIMARY KEY,
    uuid                 UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    shortcode_uuid       UUID          NOT NULL UNIQUE REFERENCES mpesa_shortcodes (uuid),
    nominated_number     VARCHAR(20)   NOT NULL,
    callback_url         VARCHAR(500)  NOT NULL,
    registered           BOOLEAN       NOT NULL DEFAULT false,
    response_status      VARCHAR(50),
    response_description VARCHAR(1000),
    response_ref_id      VARCHAR(100),
    raw                  JSONB,
    registered_by        VARCHAR(255),
    registered_at        TIMESTAMP,
    last_nightly_date    DATE,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Deposits added from a statement or Pull Transactions say so; NULL means Safaricom's own callback.
ALTER TABLE mpesa_incoming_payments ADD COLUMN source VARCHAR(20)
    CHECK (source IN ('STATEMENT', 'PULL_TRANSACTIONS'));

-- Matching looks receipts up with case and spaces ignored.
CREATE INDEX idx_mpesa_incoming_payments_receipt_upper ON mpesa_incoming_payments (upper(trim(transaction_id)));
CREATE INDEX idx_mpesa_outgoing_payments_receipt_upper ON mpesa_outgoing_payments (upper(trim(transaction_id)));
CREATE INDEX idx_b2b_transfers_receipt_upper ON b2b_transfers (upper(trim(transaction_id)));

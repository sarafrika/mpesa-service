-- Maker-checker: a money movement someone asked for waits here until a different person approves it.
-- The CHECK keeps the checker from being the maker even if application code slips; version guards
-- against two checkers deciding the same request at once.
CREATE TABLE approval_requests
(
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID           NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    type            VARCHAR(30)    NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'WAITING'
        CHECK (status IN ('WAITING', 'APPROVED', 'REJECTED', 'EXPIRED', 'EXECUTING', 'EXECUTED', 'FAILED')),
    title           VARCHAR(200)   NOT NULL,
    shortcode_uuid  UUID REFERENCES mpesa_shortcodes (uuid),
    amount          NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    reason          VARCHAR(1000)  NOT NULL,
    payload         JSONB          NOT NULL DEFAULT '{}'::jsonb,
    pre_checks      JSONB          NOT NULL DEFAULT '[]'::jsonb,
    daraja_call     VARCHAR(200),
    maker           VARCHAR(100)   NOT NULL,
    maker_name      VARCHAR(255),
    checker         VARCHAR(100),
    checker_name    VARCHAR(255),
    decision_note   VARCHAR(1000),
    decided_at      TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ    NOT NULL,
    executed_at     TIMESTAMPTZ,
    heartbeat_at    TIMESTAMPTZ,
    outcome_message VARCHAR(1000),
    version         BIGINT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_approval_checker_not_maker CHECK (checker IS NULL OR checker <> maker)
);

CREATE INDEX idx_approval_requests_status ON approval_requests (status, created_at DESC, id DESC);
CREATE INDEX idx_approval_requests_type ON approval_requests (type, status, created_at DESC);
CREATE INDEX idx_approval_requests_created_at ON approval_requests (created_at DESC, id DESC);
CREATE INDEX idx_approval_requests_amount ON approval_requests (amount, id);
CREATE INDEX idx_approval_requests_expires_at ON approval_requests (expires_at, id);
CREATE INDEX idx_approval_requests_shortcode ON approval_requests (shortcode_uuid, status);

-- Machine API calls per product per day, counted as requests finish; errors are 4xx and 5xx answers.
CREATE TABLE client_api_usage
(
    client_id    VARCHAR(100) NOT NULL,
    day          DATE         NOT NULL,
    calls        BIGINT       NOT NULL DEFAULT 0,
    errors       BIGINT       NOT NULL DEFAULT 0,
    last_call_at TIMESTAMPTZ,
    PRIMARY KEY (client_id, day)
);

CREATE INDEX idx_client_api_usage_day ON client_api_usage (day);

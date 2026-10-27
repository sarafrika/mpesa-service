-- Callbacks refused before any handler ran (unknown callback URL) are REJECTED, not FAILED.
ALTER TABLE callback_inbox DROP CONSTRAINT IF EXISTS callback_inbox_status_check;
ALTER TABLE callback_inbox
    ADD CONSTRAINT callback_inbox_status_check
        CHECK (status IN ('RECEIVED', 'PROCESSED', 'FAILED', 'REPLAYED', 'REJECTED'));

UPDATE callback_inbox SET status = 'REJECTED'
WHERE status = 'FAILED' AND error IN ('Answered HTTP 401', 'Answered HTTP 403', 'Answered HTTP 404');

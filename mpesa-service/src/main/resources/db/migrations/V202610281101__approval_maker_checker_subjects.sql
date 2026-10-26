-- Maker-checker compares the people's Keycloak subjects (JWT "sub"), which never change, instead of only their
-- usernames, which can. Rows from before this keep a null maker_sub and are still compared by username.
ALTER TABLE approval_requests ADD COLUMN maker_sub VARCHAR(255);
ALTER TABLE approval_requests ADD COLUMN checker_sub VARCHAR(255);

ALTER TABLE approval_requests ADD CONSTRAINT chk_approval_checker_sub_not_maker
    CHECK (checker_sub IS NULL OR maker_sub IS NULL OR checker_sub <> maker_sub);

CREATE INDEX idx_approval_requests_maker_sub ON approval_requests (status, maker_sub);

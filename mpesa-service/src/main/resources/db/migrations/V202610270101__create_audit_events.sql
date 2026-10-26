-- Who changed what in the console, with the record before and after (secrets redacted).
-- Append-only: the trigger refuses updates and deletes so the trail cannot be rewritten.
CREATE TABLE audit_events
(
    id          BIGSERIAL PRIMARY KEY,
    uuid        UUID         NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    actor       VARCHAR(255) NOT NULL,
    actor_name  VARCHAR(255),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id   VARCHAR(255),
    before      JSONB,
    after       JSONB,
    ip          VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_events_created_at ON audit_events (created_at DESC);
CREATE INDEX idx_audit_events_actor ON audit_events (actor);
CREATE INDEX idx_audit_events_action ON audit_events (action);
CREATE INDEX idx_audit_events_entity ON audit_events (entity_type, entity_id);

CREATE FUNCTION audit_events_append_only() RETURNS trigger AS
$$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_append_only();

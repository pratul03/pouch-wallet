CREATE TABLE audit_log (
    id           UUID        PRIMARY KEY,
    entity_type  VARCHAR(50) NOT NULL,
    entity_id    UUID        NOT NULL,
    action       VARCHAR(50) NOT NULL,
    actor_id     UUID,
    old_value    JSONB,
    new_value    JSONB,
    ip_address   INET,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id, created_at DESC);

CREATE TABLE notification_outbox (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id),
    type        VARCHAR(50) NOT NULL,
    payload     JSONB       NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts    INT         NOT NULL DEFAULT 0,
    next_retry  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_pending ON notification_outbox (status, next_retry)
    WHERE status = 'PENDING';

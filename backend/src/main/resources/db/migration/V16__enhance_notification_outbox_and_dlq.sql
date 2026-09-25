ALTER TABLE notification_outbox ADD COLUMN IF NOT EXISTS last_error TEXT;
ALTER TABLE notification_outbox ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_outbox_dlq ON notification_outbox (status, dead_lettered_at DESC)
    WHERE status = 'DEAD_LETTER';

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS disputed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS dispute_reason VARCHAR(255);
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS reversed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_transactions_pending ON transactions (status, created_at ASC)
    WHERE status = 'PENDING';

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS category VARCHAR(40) NOT NULL DEFAULT 'TRANSFER';

CREATE INDEX idx_tx_category ON transactions (category, created_at DESC);

CREATE TABLE scratch_cards (
    id                  UUID         PRIMARY KEY,
    user_id             UUID         NOT NULL REFERENCES users(id),
    title               VARCHAR(120) NOT NULL,
    description         VARCHAR(255),
    reward_amount_cents BIGINT       NOT NULL CHECK (reward_amount_cents >= 0),
    is_scratched        BOOLEAN      NOT NULL DEFAULT FALSE,
    scratched_at        TIMESTAMPTZ,
    transaction_id      UUID         REFERENCES transactions(id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_scratch_cards_user ON scratch_cards (user_id, is_scratched, created_at DESC);

CREATE TABLE transactions (
    id                  UUID         PRIMARY KEY,
    idempotency_key     VARCHAR(64)  NOT NULL UNIQUE,
    sender_wallet_id    UUID         NOT NULL REFERENCES wallets(id),
    receiver_wallet_id  UUID         NOT NULL REFERENCES wallets(id),
    amount              BIGINT       NOT NULL CHECK (amount > 0),
    currency            CHAR(3)      NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    description         VARCHAR(255),
    failure_reason      VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tx_sender      ON transactions (sender_wallet_id,   created_at DESC);
CREATE INDEX idx_tx_receiver    ON transactions (receiver_wallet_id, created_at DESC);
CREATE INDEX idx_tx_idempotency ON transactions (idempotency_key);

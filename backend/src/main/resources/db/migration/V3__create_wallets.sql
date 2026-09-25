CREATE TABLE wallets (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id),
    currency    CHAR(3)     NOT NULL DEFAULT 'USD',
    balance     BIGINT      NOT NULL DEFAULT 0 CHECK (balance >= 0),
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, currency)
);

CREATE INDEX idx_wallets_user_id ON wallets (user_id);

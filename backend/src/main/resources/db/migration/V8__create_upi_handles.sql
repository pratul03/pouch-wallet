CREATE TABLE upi_handles (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES users(id),
    vpa               VARCHAR(60)  NOT NULL UNIQUE,
    linked_wallet_id  UUID         NOT NULL REFERENCES wallets(id),
    is_default        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_upi_handles_vpa ON upi_handles (vpa);
CREATE INDEX idx_upi_handles_user ON upi_handles (user_id);

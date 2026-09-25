CREATE TABLE bank_accounts (
    id                   UUID         PRIMARY KEY,
    user_id              UUID         NOT NULL REFERENCES users(id),
    bank_name            VARCHAR(100) NOT NULL,
    account_number       VARCHAR(30)  NOT NULL,
    ifsc_code            VARCHAR(20)  NOT NULL,
    account_holder_name  VARCHAR(120) NOT NULL,
    simulated_balance    BIGINT       NOT NULL DEFAULT 5000000 CHECK (simulated_balance >= 0),
    is_primary           BOOLEAN      NOT NULL DEFAULT TRUE,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, account_number, ifsc_code)
);

CREATE INDEX idx_bank_accounts_user ON bank_accounts (user_id);

CREATE TABLE bank_transactions (
    id                UUID         PRIMARY KEY,
    bank_account_id   UUID         NOT NULL REFERENCES bank_accounts(id),
    wallet_id         UUID         NOT NULL REFERENCES wallets(id),
    type              VARCHAR(30)  NOT NULL, -- DEPOSIT_TO_WALLET | WITHDRAW_TO_BANK
    amount            BIGINT       NOT NULL CHECK (amount > 0),
    currency          CHAR(3)      NOT NULL DEFAULT 'USD',
    status            VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED',
    reference_number  VARCHAR(64)  NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bank_tx_user ON bank_transactions (bank_account_id, created_at DESC);

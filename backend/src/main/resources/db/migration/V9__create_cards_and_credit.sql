CREATE TABLE cards (
    id                   UUID         PRIMARY KEY,
    user_id              UUID         NOT NULL REFERENCES users(id),
    wallet_id            UUID         REFERENCES wallets(id),
    card_type            VARCHAR(20)  NOT NULL, -- DEBIT | CREDIT
    card_network         VARCHAR(20)  NOT NULL DEFAULT 'RUPAY', -- RUPAY | VISA | MASTERCARD
    card_number_masked   VARCHAR(24)  NOT NULL,
    card_number_full     VARCHAR(255) NOT NULL,
    expiry_month         INT          NOT NULL,
    expiry_year          INT          NOT NULL,
    cvv_plain            VARCHAR(10)  NOT NULL,
    card_holder_name     VARCHAR(120) NOT NULL,
    daily_limit_cents    BIGINT       NOT NULL DEFAULT 5000000,
    online_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | FROZEN | BLOCKED
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cards_user ON cards (user_id);

CREATE TABLE credit_accounts (
    id                     UUID         PRIMARY KEY,
    user_id                UUID         NOT NULL UNIQUE REFERENCES users(id),
    card_id                UUID         REFERENCES cards(id),
    total_credit_limit     BIGINT       NOT NULL DEFAULT 2500000,
    available_credit_limit BIGINT       NOT NULL DEFAULT 2500000 CHECK (available_credit_limit >= 0),
    current_bill_amount    BIGINT       NOT NULL DEFAULT 0,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_accounts_user ON credit_accounts (user_id);

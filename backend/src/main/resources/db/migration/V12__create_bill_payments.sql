CREATE TABLE bill_payments (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users(id),
    wallet_id        UUID         NOT NULL REFERENCES wallets(id),
    category         VARCHAR(40)  NOT NULL, -- 'MOBILE_RECHARGE', 'ELECTRICITY', 'WATER', 'BROADBAND', 'DTH', 'GAS'
    biller_id        VARCHAR(60)  NOT NULL,
    biller_name      VARCHAR(120) NOT NULL,
    consumer_number  VARCHAR(80)  NOT NULL,
    amount_cents     BIGINT       NOT NULL CHECK (amount_cents > 0),
    status           VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS', -- 'SUCCESS', 'FAILED', 'PENDING'
    reference_number VARCHAR(60)  NOT NULL UNIQUE,
    transaction_id   UUID         REFERENCES transactions(id),
    metadata         JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bill_payments_user ON bill_payments (user_id, created_at DESC);
CREATE INDEX idx_bill_payments_ref  ON bill_payments (reference_number);

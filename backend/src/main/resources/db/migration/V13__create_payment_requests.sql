CREATE TABLE payment_requests (
    id                  UUID         PRIMARY KEY,
    requester_user_id   UUID         NOT NULL REFERENCES users(id),
    payer_user_id       UUID         REFERENCES users(id),
    payer_phone         VARCHAR(20),
    payer_vpa           VARCHAR(60),
    amount_cents        BIGINT       NOT NULL CHECK (amount_cents > 0),
    note                VARCHAR(255),
    split_group_id      UUID,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- 'PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'CANCELLED'
    transaction_id      UUID         REFERENCES transactions(id),
    expires_at          TIMESTAMPTZ  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_requests_requester ON payment_requests (requester_user_id, created_at DESC);
CREATE INDEX idx_payment_requests_payer_id  ON payment_requests (payer_user_id, created_at DESC);
CREATE INDEX idx_payment_requests_payer_phone ON payment_requests (payer_phone, status);
CREATE INDEX idx_payment_requests_payer_vpa   ON payment_requests (payer_vpa, status);
CREATE INDEX idx_payment_requests_split_group ON payment_requests (split_group_id);

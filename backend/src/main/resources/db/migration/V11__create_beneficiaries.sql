CREATE TABLE beneficiaries (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL REFERENCES users(id),
    name           VARCHAR(120) NOT NULL,
    nickname       VARCHAR(60),
    phone          VARCHAR(20),
    vpa            VARCHAR(60),
    account_number VARCHAR(30),
    ifsc_code      VARCHAR(20),
    is_favorite    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (phone IS NOT NULL OR vpa IS NOT NULL OR (account_number IS NOT NULL AND ifsc_code IS NOT NULL))
);

CREATE INDEX idx_beneficiaries_user ON beneficiaries (user_id, created_at DESC);

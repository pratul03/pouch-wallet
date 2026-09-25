CREATE TABLE otp_verifications (
    id          UUID         PRIMARY KEY,
    phone       VARCHAR(20)  NOT NULL,
    otp_hash    VARCHAR(255) NOT NULL,
    purpose     VARCHAR(30)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_otp_phone_purpose ON otp_verifications (phone, purpose, used);

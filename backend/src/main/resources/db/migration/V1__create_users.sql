CREATE TABLE users (
    id          UUID         PRIMARY KEY,
    phone       VARCHAR(20)  NOT NULL UNIQUE,
    full_name   VARCHAR(120) NOT NULL,
    pin_hash    VARCHAR(255) NOT NULL,
    fcm_token   VARCHAR(512),
    kyc_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_phone ON users (phone);

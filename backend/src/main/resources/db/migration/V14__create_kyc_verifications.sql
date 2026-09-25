ALTER TABLE users ADD COLUMN IF NOT EXISTS kyc_tier INT NOT NULL DEFAULT 0;

CREATE TABLE kyc_verifications (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users(id),
    id_type          VARCHAR(40)  NOT NULL, -- 'PASSPORT', 'NATIONAL_ID', 'DRIVING_LICENSE', 'PAN'
    document_number  VARCHAR(80)  NOT NULL,
    document_url     VARCHAR(500),
    status           VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED', -- 'SUBMITTED', 'APPROVED', 'REJECTED'
    rejection_reason VARCHAR(255),
    reviewed_by      UUID         REFERENCES users(id),
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_kyc_verifications_user   ON kyc_verifications (user_id, created_at DESC);
CREATE INDEX idx_kyc_verifications_status ON kyc_verifications (status, created_at ASC);

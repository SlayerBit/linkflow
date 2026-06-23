CREATE TABLE password_reset_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_password_reset_tokens_token_hash ON password_reset_tokens (token_hash);

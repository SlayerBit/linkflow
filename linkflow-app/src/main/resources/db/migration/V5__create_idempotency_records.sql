CREATE TABLE idempotency_records (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(255)  NOT NULL,
    endpoint        VARCHAR(255)  NOT NULL,
    response_status INT           NOT NULL,
    response_body   TEXT          NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT uq_idempotency_user_endpoint_key UNIQUE (user_id, endpoint, idempotency_key)
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_records (expires_at);

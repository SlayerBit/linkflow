CREATE TABLE short_urls (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    short_code    VARCHAR(100)  NOT NULL,
    original_url  VARCHAR(2048) NOT NULL,
    custom_alias  VARCHAR(100),
    owner_id      UUID          NOT NULL REFERENCES users(id),
    expires_at    TIMESTAMPTZ,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by    VARCHAR(255),
    updated_by    VARCHAR(255),

    CONSTRAINT uq_short_urls_short_code UNIQUE (short_code)
);

CREATE INDEX idx_short_urls_short_code ON short_urls (lower(short_code));
CREATE INDEX idx_short_urls_owner_id ON short_urls (owner_id);
CREATE INDEX idx_short_urls_expires_at ON short_urls (expires_at) WHERE deleted = FALSE AND active = TRUE;
CREATE INDEX idx_short_urls_deleted ON short_urls (deleted);
CREATE INDEX idx_short_urls_active ON short_urls (active) WHERE deleted = FALSE;

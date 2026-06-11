CREATE TABLE url_analytics (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    short_url_id     UUID        NOT NULL UNIQUE REFERENCES short_urls(id) ON DELETE CASCADE,
    total_clicks     BIGINT      NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE click_events (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    short_url_id  UUID         NOT NULL REFERENCES short_urls(id) ON DELETE CASCADE,
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(512),
    referer       VARCHAR(2048),
    clicked_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_click_events_short_url_id ON click_events (short_url_id);
CREATE INDEX idx_click_events_clicked_at ON click_events (clicked_at);

-- Composite index for per-URL trend queries at scale
CREATE INDEX idx_click_events_short_url_id_clicked_at ON click_events (short_url_id, clicked_at);

-- Deduplicate click events flushed from Redis Stream (at-least-once delivery)
ALTER TABLE click_events ADD COLUMN stream_record_id VARCHAR(64);

CREATE UNIQUE INDEX uq_click_events_stream_record_id
    ON click_events (stream_record_id)
    WHERE stream_record_id IS NOT NULL;

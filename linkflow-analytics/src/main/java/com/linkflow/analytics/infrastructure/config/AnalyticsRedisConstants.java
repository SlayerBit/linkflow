package com.linkflow.analytics.infrastructure.config;

/**
 * Redis key constants for analytics buffering.
 * <p>
 * Click events are buffered in a Redis Stream and counters are accumulated
 * in Redis hashes. A scheduled flush job drains the stream and syncs counters
 * to PostgreSQL.
 */
public final class AnalyticsRedisConstants {

    private AnalyticsRedisConstants() {}

    /**
     * Redis Stream key for buffered click events.
     * Each entry contains: shortUrlId, ipAddress, userAgent, referer, clickedAt.
     */
    public static final String CLICK_STREAM_KEY = "analytics:clicks:stream";

    /**
     * Consumer group name for the click stream.
     */
    public static final String CLICK_STREAM_GROUP = "analytics-flush-group";

    /**
     * Consumer name prefix. Each app instance uses a unique consumer name.
     */
    public static final String CLICK_STREAM_CONSUMER_PREFIX = "flush-consumer-";

    /**
     * Redis Hash key prefix for per-URL click counters.
     * Full key: analytics:counter:{shortUrlId}
     * Field: "total" — accumulated click count since last flush.
     */
    public static final String COUNTER_KEY_PREFIX = "analytics:counter:";

    /**
     * Redis Set tracking which shortUrlIds have pending counter increments.
     * Avoids the need for KEYS or SCAN to find active counters during flush.
     */
    public static final String ACTIVE_COUNTERS_SET = "analytics:active_urls";

    /** Field name in the counter hash. */
    public static final String COUNTER_FIELD_TOTAL = "total";
}

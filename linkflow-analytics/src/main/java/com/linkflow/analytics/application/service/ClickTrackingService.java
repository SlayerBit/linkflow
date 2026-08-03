package com.linkflow.analytics.application.service;

import com.linkflow.analytics.infrastructure.config.AnalyticsRedisConstants;
import com.linkflow.common.port.ClickTrackingPort.ClickTrackingCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Click tracking service that buffers events in Redis before periodic flush to PostgreSQL.
 * <p>
 * On each click:
 * <ol>
 *   <li>Push event details to a Redis Stream ({@code analytics:clicks:stream})</li>
 *   <li>Increment a per-URL counter in a Redis Hash ({@code analytics:counter:{id}})</li>
 *   <li>Track the URL ID in a Redis Set ({@code analytics:active_urls})</li>
 * </ol>
 * <p>
 * A scheduled {@link AnalyticsFlushService} periodically drains the stream and
 * syncs counters to PostgreSQL.
 * <p>
 * <b>Fallback:</b> If Redis is unavailable, falls back to direct synchronous DB writes
 * (original behavior) to avoid silent data loss.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickTrackingService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ClickPersistenceService clickPersistenceService;

    /**
     * Buffer a click event in Redis for asynchronous flush.
     * Falls back to direct DB write if Redis is unavailable.
     */
    @Async("clickTrackingExecutor")
    public void trackClick(ClickTrackingCommand command) {
        try {
            bufferToRedis(command);
        } catch (Exception ex) {
            log.warn("Redis buffering failed for shortUrlId={}, falling back to direct DB write: {}",
                    command.shortUrlId(), ex.getMessage());
            fallbackWrite(command);
        }
    }

    /**
     * Writes the click straight to the database when Redis is unavailable.
     * <p>
     * Delegated to a separate bean rather than a local method so the call goes through the Spring
     * proxy: a self-invoked {@code @Transactional} method is not proxied at all, which would leave
     * the click-event insert and the counter update running as two independent auto-commits with
     * no transaction around them.
     */
    private void fallbackWrite(ClickTrackingCommand command) {
        try {
            clickPersistenceService.recordClick(command);
        } catch (Exception ex) {
            // Analytics must never break a redirect. The visitor still gets their destination.
            log.warn("Direct DB write failed for shortUrlId={}: {}",
                    command.shortUrlId(), ex.getMessage());
        }
    }

    /**
     * Buffer click event to Redis Stream and increment counter.
     */
    private void bufferToRedis(ClickTrackingCommand command) {
        String shortUrlId = command.shortUrlId().toString();
        Instant clickedAt = Instant.now();

        // 1. Push event details to Redis Stream
        Map<String, String> eventFields = new HashMap<>();
        eventFields.put("shortUrlId", shortUrlId);
        eventFields.put("ipAddress", command.ipAddress() != null ? command.ipAddress() : "");
        eventFields.put("userAgent", command.userAgent() != null ? command.userAgent() : "");
        eventFields.put("referer", command.referer() != null ? command.referer() : "");
        eventFields.put("clickedAt", clickedAt.toString());

        MapRecord<String, String, String> record = MapRecord.create(
                AnalyticsRedisConstants.CLICK_STREAM_KEY, eventFields);
        stringRedisTemplate.opsForStream().add(record);

        // 2. Increment per-URL counter
        String counterKey = AnalyticsRedisConstants.COUNTER_KEY_PREFIX + shortUrlId;
        stringRedisTemplate.opsForHash()
                .increment(counterKey, AnalyticsRedisConstants.COUNTER_FIELD_TOTAL, 1);

        // 3. Track active URL in set (avoids KEYS/SCAN during flush)
        stringRedisTemplate.opsForSet()
                .add(AnalyticsRedisConstants.ACTIVE_COUNTERS_SET, shortUrlId);
    }

}

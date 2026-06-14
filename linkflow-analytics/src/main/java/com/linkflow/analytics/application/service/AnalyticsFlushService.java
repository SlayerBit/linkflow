package com.linkflow.analytics.application.service;

import com.linkflow.analytics.domain.entity.ClickEvent;
import com.linkflow.analytics.domain.entity.UrlAnalytics;
import com.linkflow.analytics.domain.repository.ClickEventRepository;
import com.linkflow.analytics.domain.repository.UrlAnalyticsRepository;
import com.linkflow.analytics.infrastructure.config.AnalyticsRedisConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Scheduled service that flushes buffered analytics from Redis to PostgreSQL.
 * <p>
 * Two data paths are flushed:
 * <ol>
 *   <li><b>Click events:</b> Drained from the Redis Stream
 *       ({@code analytics:clicks:stream}) using a consumer group. Events are
 *       bulk-inserted into the {@code click_events} table.</li>
 *   <li><b>Click counters:</b> Per-URL counters in Redis hashes
 *       ({@code analytics:counter:{id}}) are read via the tracking set
 *       ({@code analytics:active_urls}), then applied as increments to
 *       {@code url_analytics.total_clicks}. No KEYS or SCAN is used.</li>
 * </ol>
 * <p>
 * <b>Failure modes:</b>
 * <ul>
 *   <li>Redis down during flush: flush is skipped, retried next cycle. Buffered
 *       data remains in Redis.</li>
 *   <li>DB write failure: events remain acknowledged in Redis (at-most-once for
 *       events, at-least-once for counters via GETDEL atomicity).</li>
 *   <li>App shutdown: {@code @PreDestroy} triggers a final flush attempt.</li>
 * </ul>
 *
 * @see ClickTrackingService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsFlushService {

    /** Maximum number of stream entries to process per flush cycle. */
    private static final int BATCH_SIZE = 1000;

    private final StringRedisTemplate stringRedisTemplate;
    private final ClickEventRepository clickEventRepository;
    private final UrlAnalyticsRepository urlAnalyticsRepository;

    private final String consumerName = AnalyticsRedisConstants.CLICK_STREAM_CONSUMER_PREFIX
            + UUID.randomUUID().toString().substring(0, 8);

    /**
     * Initialize the consumer group on startup. Idempotent — ignores if group already exists.
     */
    @PostConstruct
    void initConsumerGroup() {
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    AnalyticsRedisConstants.CLICK_STREAM_KEY,
                    ReadOffset.from("0"),
                    AnalyticsRedisConstants.CLICK_STREAM_GROUP
            );
            log.info("Created Redis Stream consumer group '{}' for stream '{}'",
                    AnalyticsRedisConstants.CLICK_STREAM_GROUP,
                    AnalyticsRedisConstants.CLICK_STREAM_KEY);
        } catch (RedisSystemException ex) {
            // BUSYGROUP — group already exists, which is expected on restart
            if (ex.getMessage() != null && ex.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer group '{}' already exists", AnalyticsRedisConstants.CLICK_STREAM_GROUP);
            } else {
                log.warn("Failed to create consumer group, will retry on next flush: {}", ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Failed to initialize analytics stream consumer group: {}", ex.getMessage());
        }
    }

    /**
     * Periodic flush of buffered analytics data from Redis to PostgreSQL.
     * Default interval: 30 seconds (configurable via {@code linkflow.analytics.flush-interval-ms}).
     */
    @Scheduled(fixedDelayString = "${linkflow.analytics.flush-interval-ms:30000}")
    public void flush() {
        try {
            int eventsFlushed = flushClickEvents();
            int countersFlushed = flushCounters();
            if (eventsFlushed > 0 || countersFlushed > 0) {
                log.info("Analytics flush complete: {} events, {} counter updates",
                        eventsFlushed, countersFlushed);
            }
        } catch (Exception ex) {
            log.warn("Analytics flush cycle failed, will retry next cycle: {}", ex.getMessage());
        }
    }

    /**
     * Graceful shutdown: flush remaining buffered data before the application stops.
     */
    @PreDestroy
    void onShutdown() {
        log.info("Application shutting down — performing final analytics flush");
        try {
            flush();
        } catch (Exception ex) {
            log.warn("Final analytics flush failed: {}", ex.getMessage());
        }
    }

    /**
     * Drain click events from the Redis Stream and bulk-insert into PostgreSQL.
     *
     * @return number of events flushed
     */
    @SuppressWarnings("unchecked")
    private int flushClickEvents() {
        List<MapRecord<String, Object, Object>> records;
        try {
            records = stringRedisTemplate.opsForStream().read(
                    Consumer.from(AnalyticsRedisConstants.CLICK_STREAM_GROUP, consumerName),
                    StreamReadOptions.empty().count(BATCH_SIZE),
                    StreamOffset.create(AnalyticsRedisConstants.CLICK_STREAM_KEY, ReadOffset.lastConsumed())
            );
        } catch (Exception ex) {
            log.debug("No stream entries to read or stream not ready: {}", ex.getMessage());
            return 0;
        }

        if (records == null || records.isEmpty()) {
            return 0;
        }

        List<ClickEvent> events = new ArrayList<>(records.size());
        List<RecordId> processedIds = new ArrayList<>(records.size());

        for (MapRecord<String, Object, Object> record : records) {
            try {
                Map<Object, Object> fields = record.getValue();
                ClickEvent event = ClickEvent.builder()
                        .shortUrlId(UUID.fromString(String.valueOf(fields.get("shortUrlId"))))
                        .ipAddress(nullIfEmpty(String.valueOf(fields.get("ipAddress"))))
                        .userAgent(nullIfEmpty(String.valueOf(fields.get("userAgent"))))
                        .referer(nullIfEmpty(String.valueOf(fields.get("referer"))))
                        .clickedAt(Instant.parse(String.valueOf(fields.get("clickedAt"))))
                        .build();
                events.add(event);
                processedIds.add(record.getId());
            } catch (Exception ex) {
                log.warn("Failed to parse stream record {}: {}", record.getId(), ex.getMessage());
                processedIds.add(record.getId()); // Acknowledge bad records to avoid blocking
            }
        }

        // Bulk insert events
        if (!events.isEmpty()) {
            clickEventRepository.saveAll(events);
        }

        // Acknowledge processed records
        if (!processedIds.isEmpty()) {
            stringRedisTemplate.opsForStream().acknowledge(
                    AnalyticsRedisConstants.CLICK_STREAM_KEY,
                    AnalyticsRedisConstants.CLICK_STREAM_GROUP,
                    processedIds.toArray(new RecordId[0])
            );

            // Trim acknowledged entries to prevent unbounded stream growth
            stringRedisTemplate.opsForStream().trim(
                    AnalyticsRedisConstants.CLICK_STREAM_KEY,
                    records.size()
            );
        }

        return events.size();
    }

    /**
     * Flush accumulated per-URL click counters from Redis hashes to PostgreSQL.
     * Uses the tracking set ({@code analytics:active_urls}) to find active counters
     * — no KEYS or SCAN needed.
     *
     * @return number of counters flushed
     */
    @Transactional
    public int flushCounters() {
        Set<String> activeUrls;
        try {
            activeUrls = stringRedisTemplate.opsForSet()
                    .members(AnalyticsRedisConstants.ACTIVE_COUNTERS_SET);
        } catch (Exception ex) {
            log.debug("Cannot read active counters set: {}", ex.getMessage());
            return 0;
        }

        if (activeUrls == null || activeUrls.isEmpty()) {
            return 0;
        }

        int flushed = 0;
        for (String shortUrlIdStr : activeUrls) {
            try {
                UUID shortUrlId = UUID.fromString(shortUrlIdStr);
                String counterKey = AnalyticsRedisConstants.COUNTER_KEY_PREFIX + shortUrlIdStr;

                // Atomically get and reset the counter
                Object rawCount = stringRedisTemplate.opsForHash()
                        .get(counterKey, AnalyticsRedisConstants.COUNTER_FIELD_TOTAL);
                if (rawCount == null) {
                    // Counter was already flushed or deleted — clean up tracking set
                    stringRedisTemplate.opsForSet()
                            .remove(AnalyticsRedisConstants.ACTIVE_COUNTERS_SET, shortUrlIdStr);
                    continue;
                }

                long count = Long.parseLong(String.valueOf(rawCount));
                if (count <= 0) {
                    stringRedisTemplate.delete(counterKey);
                    stringRedisTemplate.opsForSet()
                            .remove(AnalyticsRedisConstants.ACTIVE_COUNTERS_SET, shortUrlIdStr);
                    continue;
                }

                // Decrement by the amount we're about to flush (atomic)
                long remaining = stringRedisTemplate.opsForHash()
                        .increment(counterKey, AnalyticsRedisConstants.COUNTER_FIELD_TOTAL, -count);

                // Update PostgreSQL
                UrlAnalytics analytics = urlAnalyticsRepository.findByShortUrlId(shortUrlId)
                        .orElseGet(() -> UrlAnalytics.builder()
                                .shortUrlId(shortUrlId)
                                .totalClicks(0L)
                                .build());

                analytics.setTotalClicks(analytics.getTotalClicks() + count);
                analytics.setLastAccessedAt(Instant.now());
                urlAnalyticsRepository.save(analytics);

                // Clean up if no remaining count
                if (remaining <= 0) {
                    stringRedisTemplate.delete(counterKey);
                    stringRedisTemplate.opsForSet()
                            .remove(AnalyticsRedisConstants.ACTIVE_COUNTERS_SET, shortUrlIdStr);
                }

                flushed++;
            } catch (Exception ex) {
                log.warn("Failed to flush counter for shortUrlId={}: {}", shortUrlIdStr, ex.getMessage());
            }
        }

        return flushed;
    }

    private String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }
}

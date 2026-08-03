package com.linkflow.analytics.application.service;

import com.linkflow.analytics.domain.entity.ClickEvent;
import com.linkflow.analytics.infrastructure.config.AnalyticsRedisConstants;
import com.linkflow.analytics.infrastructure.config.AnalyticsStreamProperties;
import com.linkflow.analytics.infrastructure.redis.AnalyticsCounterFlushScript;
import com.linkflow.analytics.infrastructure.redis.AnalyticsCounterFlushScript.CounterFlushResult;
import com.linkflow.common.metrics.LinkflowMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsFlushService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AnalyticsCounterFlushScript analyticsCounterFlushScript;
    private final AnalyticsFlushPersistenceService analyticsFlushPersistenceService;
    private final AnalyticsStreamProperties streamProperties;
    private final LinkflowMetrics metrics;

    private final String consumerName = AnalyticsRedisConstants.CLICK_STREAM_CONSUMER_PREFIX
            + UUID.randomUUID().toString().substring(0, 8);

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
            if (ex.getMessage() != null && ex.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer group '{}' already exists", AnalyticsRedisConstants.CLICK_STREAM_GROUP);
            } else {
                log.warn("Failed to create consumer group, will retry on next flush: {}", ex.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Failed to initialize analytics stream consumer group: {}", ex.getMessage());
        }
    }

    public void flush() {
        try {
            int eventsFlushed = flushClickEvents();
            int countersFlushed = flushCounters();
            if (eventsFlushed > 0) {
                metrics.analyticsFlush("click_events", eventsFlushed);
            }
            if (countersFlushed > 0) {
                metrics.analyticsFlush("counters", countersFlushed);
            }
            if (eventsFlushed > 0 || countersFlushed > 0) {
                log.info("Analytics flush complete: {} events, {} counter updates",
                        eventsFlushed, countersFlushed);
            }
            reapIdleConsumers();
            enforceStreamSafetyLimit();
        } catch (Exception ex) {
            metrics.analyticsFlushFailed();
            log.warn("Analytics flush cycle failed, will retry next cycle: {}", ex.getMessage());
        }
    }

    @PreDestroy
    void onShutdown() {
        log.info("Application shutting down — performing final analytics flush");
        try {
            flush();
        } catch (Exception ex) {
            log.warn("Final analytics flush failed: {}", ex.getMessage());
        }
    }

    protected int flushClickEvents() {
        List<MapRecord<String, Object, Object>> records = new ArrayList<>();
        // Stale entries first: they are the oldest data and the ones at risk of being stranded.
        records.addAll(reclaimStalePendingEntries());
        records.addAll(readNewEntries());

        if (records.isEmpty()) {
            return 0;
        }

        int inserted = 0;
        List<RecordId> processedIds = new ArrayList<>(records.size());

        for (MapRecord<String, Object, Object> record : records) {
            try {
                // No pre-check for an existing row: the insert path already de-duplicates on the
                // stream record id, and checking first doubled the database round trips per entry.
                if (analyticsFlushPersistenceService.persistClickEventIfAbsent(toClickEvent(record))) {
                    inserted++;
                }
                processedIds.add(record.getId());
            } catch (Exception ex) {
                log.warn("Failed to persist stream record {} — leaving pending for retry: {}",
                        record.getId(), ex.getMessage());
            }
        }

        acknowledgeAndDelete(processedIds);
        return inserted;
    }

    private ClickEvent toClickEvent(MapRecord<String, Object, Object> record) {
        Map<Object, Object> fields = record.getValue();
        return ClickEvent.builder()
                .shortUrlId(UUID.fromString(String.valueOf(fields.get("shortUrlId"))))
                .ipAddress(nullIfEmpty(String.valueOf(fields.get("ipAddress"))))
                .userAgent(nullIfEmpty(String.valueOf(fields.get("userAgent"))))
                .referer(nullIfEmpty(String.valueOf(fields.get("referer"))))
                .clickedAt(Instant.parse(String.valueOf(fields.get("clickedAt"))))
                .streamRecordId(record.getId().getValue())
                .build();
    }

    private List<MapRecord<String, Object, Object>> readNewEntries() {
        try {
            List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                    Consumer.from(AnalyticsRedisConstants.CLICK_STREAM_GROUP, consumerName),
                    StreamReadOptions.empty().count(streamProperties.getBatchSize()),
                    StreamOffset.create(AnalyticsRedisConstants.CLICK_STREAM_KEY, ReadOffset.lastConsumed())
            );
            return records == null ? List.of() : records;
        } catch (Exception ex) {
            log.debug("No stream entries to read or stream not ready: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Takes ownership of entries that another consumer read but never acknowledged, so a crashed
     * or replaced instance does not strand the clicks it had in flight.
     */
    private List<MapRecord<String, Object, Object>> reclaimStalePendingEntries() {
        Duration reclaimAfter = streamProperties.getReclaimPendingAfter();
        try {
            PendingMessages pending = stringRedisTemplate.opsForStream().pending(
                    AnalyticsRedisConstants.CLICK_STREAM_KEY,
                    AnalyticsRedisConstants.CLICK_STREAM_GROUP,
                    Range.unbounded(),
                    streamProperties.getBatchSize());

            if (pending == null || pending.isEmpty()) {
                return List.of();
            }

            List<PendingMessage> stalePending = pending.stream()
                    .filter(message -> message.getElapsedTimeSinceLastDelivery()
                            .compareTo(reclaimAfter) >= 0)
                    .toList();

            discardExhaustedEntries(stalePending);

            RecordId[] stale = stalePending.stream()
                    .filter(message -> message.getTotalDeliveryCount()
                            <= streamProperties.getMaxDeliveryAttempts())
                    .map(PendingMessage::getId)
                    .toArray(RecordId[]::new);

            if (stale.length == 0) {
                return List.of();
            }

            List<MapRecord<String, Object, Object>> claimed = stringRedisTemplate.opsForStream().claim(
                    AnalyticsRedisConstants.CLICK_STREAM_KEY,
                    AnalyticsRedisConstants.CLICK_STREAM_GROUP,
                    consumerName,
                    XClaimOptions.minIdle(reclaimAfter).ids(stale));

            if (claimed != null && !claimed.isEmpty()) {
                log.info("Reclaimed {} stale pending click events from inactive consumers", claimed.size());
            }
            return claimed == null ? List.of() : claimed;
        } catch (Exception ex) {
            log.debug("Could not reclaim pending stream entries: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Drops entries that have failed too many times, so one unpersistable click cannot be retried
     * on every cycle indefinitely. Logged at warn because it is real, if minor, data loss.
     */
    private void discardExhaustedEntries(List<PendingMessage> stalePending) {
        RecordId[] exhausted = stalePending.stream()
                .filter(message -> message.getTotalDeliveryCount()
                        > streamProperties.getMaxDeliveryAttempts())
                .map(PendingMessage::getId)
                .toArray(RecordId[]::new);

        if (exhausted.length == 0) {
            return;
        }

        log.warn("Discarding {} click event(s) that could not be persisted after {} attempts",
                exhausted.length, streamProperties.getMaxDeliveryAttempts());
        acknowledgeAndDelete(List.of(exhausted));
    }

    /**
     * Acknowledges processed entries and removes them from the stream.
     * <p>
     * Acknowledging alone only clears the pending list — the entry itself stays in the stream
     * forever, so the stream would grow without bound. There is a single consumer group, so once
     * an entry is safely in PostgreSQL nothing else will ever need to read it.
     */
    private void acknowledgeAndDelete(List<RecordId> processedIds) {
        if (processedIds.isEmpty()) {
            return;
        }
        RecordId[] ids = processedIds.toArray(new RecordId[0]);
        try {
            stringRedisTemplate.opsForStream().acknowledge(
                    AnalyticsRedisConstants.CLICK_STREAM_KEY,
                    AnalyticsRedisConstants.CLICK_STREAM_GROUP,
                    ids);
            stringRedisTemplate.opsForStream()
                    .delete(AnalyticsRedisConstants.CLICK_STREAM_KEY, ids);
        } catch (Exception ex) {
            log.warn("Failed to acknowledge or delete {} processed stream entries: {}",
                    ids.length, ex.getMessage());
        }
    }

    private void reapIdleConsumers() {
        try {
            StreamInfo.XInfoConsumers consumers = stringRedisTemplate.opsForStream()
                    .consumers(AnalyticsRedisConstants.CLICK_STREAM_KEY,
                            AnalyticsRedisConstants.CLICK_STREAM_GROUP);

            for (StreamInfo.XInfoConsumer consumer : consumers) {
                boolean isSelf = consumerName.equals(consumer.consumerName());
                boolean hasPending = consumer.pendingCount() > 0;
                boolean idleTooLong = consumer.idleTime()
                        .compareTo(streamProperties.getRemoveConsumerAfterIdle()) >= 0;

                if (!isSelf && !hasPending && idleTooLong) {
                    stringRedisTemplate.opsForStream().deleteConsumer(
                            AnalyticsRedisConstants.CLICK_STREAM_KEY,
                            Consumer.from(AnalyticsRedisConstants.CLICK_STREAM_GROUP,
                                    consumer.consumerName()));
                    log.info("Removed idle stream consumer '{}'", consumer.consumerName());
                }
            }
        } catch (Exception ex) {
            log.debug("Could not reap idle stream consumers: {}", ex.getMessage());
        }
    }

    private void enforceStreamSafetyLimit() {
        try {
            // Approximate trimming lets Redis cut on node boundaries, which is far cheaper and is
            // fine for a bound that only exists to prevent runaway memory use.
            stringRedisTemplate.opsForStream().trim(
                    AnalyticsRedisConstants.CLICK_STREAM_KEY, streamProperties.getMaxLength(), true);
        } catch (Exception ex) {
            log.debug("Could not trim analytics stream: {}", ex.getMessage());
        }
    }

    protected int flushCounters() {
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
                CounterFlushResult result = analyticsCounterFlushScript.flushCounter(shortUrlIdStr);

                if (result.flushedCount() <= 0) {
                    if (result.remainingCount() <= 0) {
                        cleanupCounterKeys(shortUrlIdStr);
                    }
                    continue;
                }

                analyticsFlushPersistenceService.applyCounterFlush(shortUrlId, result.flushedCount());

                if (result.remainingCount() <= 0) {
                    cleanupCounterKeys(shortUrlIdStr);
                }

                flushed++;
            } catch (Exception ex) {
                log.warn("Failed to flush counter for shortUrlId={}: {}", shortUrlIdStr, ex.getMessage());
            }
        }

        return flushed;
    }

    private void cleanupCounterKeys(String shortUrlIdStr) {
        stringRedisTemplate.delete(AnalyticsRedisConstants.COUNTER_KEY_PREFIX + shortUrlIdStr);
        stringRedisTemplate.opsForSet()
                .remove(AnalyticsRedisConstants.ACTIVE_COUNTERS_SET, shortUrlIdStr);
    }

    private String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }
}

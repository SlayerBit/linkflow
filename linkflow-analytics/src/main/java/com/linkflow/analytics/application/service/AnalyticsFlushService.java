package com.linkflow.analytics.application.service;

import com.linkflow.analytics.domain.entity.ClickEvent;
import com.linkflow.analytics.domain.repository.ClickEventRepository;
import com.linkflow.analytics.infrastructure.config.AnalyticsRedisConstants;
import com.linkflow.analytics.infrastructure.redis.AnalyticsCounterFlushScript;
import com.linkflow.analytics.infrastructure.redis.AnalyticsCounterFlushScript.CounterFlushResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsFlushService {

    private static final int BATCH_SIZE = 1000;

    private final StringRedisTemplate stringRedisTemplate;
    private final ClickEventRepository clickEventRepository;
    private final AnalyticsCounterFlushScript analyticsCounterFlushScript;
    private final AnalyticsFlushPersistenceService analyticsFlushPersistenceService;

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
            if (eventsFlushed > 0 || countersFlushed > 0) {
                log.info("Analytics flush complete: {} events, {} counter updates",
                        eventsFlushed, countersFlushed);
            }
        } catch (Exception ex) {
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

        int inserted = 0;
        List<RecordId> processedIds = new ArrayList<>(records.size());

        for (MapRecord<String, Object, Object> record : records) {
            String streamRecordId = record.getId().getValue();
            try {
                if (!clickEventRepository.existsByStreamRecordId(streamRecordId)) {
                    Map<Object, Object> fields = record.getValue();
                    ClickEvent event = ClickEvent.builder()
                            .shortUrlId(UUID.fromString(String.valueOf(fields.get("shortUrlId"))))
                            .ipAddress(nullIfEmpty(String.valueOf(fields.get("ipAddress"))))
                            .userAgent(nullIfEmpty(String.valueOf(fields.get("userAgent"))))
                            .referer(nullIfEmpty(String.valueOf(fields.get("referer"))))
                            .clickedAt(Instant.parse(String.valueOf(fields.get("clickedAt"))))
                            .streamRecordId(streamRecordId)
                            .build();
                    if (analyticsFlushPersistenceService.persistClickEventIfAbsent(event)) {
                        inserted++;
                    }
                }
                processedIds.add(record.getId());
            } catch (Exception ex) {
                log.warn("Failed to parse stream record {} — leaving pending for retry: {}", record.getId(), ex.getMessage());
            }
        }

        acknowledgeOnly(processedIds);
        return inserted;
    }

    private void acknowledgeOnly(List<RecordId> processedIds) {
        if (processedIds.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForStream().acknowledge(
                AnalyticsRedisConstants.CLICK_STREAM_KEY,
                AnalyticsRedisConstants.CLICK_STREAM_GROUP,
                processedIds.toArray(new RecordId[0])
        );
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

package com.linkflow.app;

import com.linkflow.analytics.application.service.AnalyticsFlushService;
import com.linkflow.analytics.domain.repository.ClickEventRepository;
import com.linkflow.analytics.infrastructure.config.AnalyticsRedisConstants;
import com.jayway.jsonpath.JsonPath;
import com.linkflow.app.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the housekeeping around the click-event stream, which is invisible in normal operation but
 * determines whether the buffer stays bounded and whether clicks survive an instance dying.
 */
class AnalyticsStreamLifecycleIT extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void reclaimImmediately(DynamicPropertyRegistry registry) {
        // Zero so a single flush reclaims what a simulated dead consumer left behind, instead of
        // the test having to wait out the production threshold.
        registry.add("linkflow.analytics.stream.reclaim-pending-after", () -> "0s");
        registry.add("linkflow.analytics.stream.remove-consumer-after-idle", () -> "0s");
    }

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private AnalyticsFlushService flushService;

    @Autowired
    private ClickEventRepository clickEventRepository;

    /** A real short URL: click_events carries a foreign key to short_urls. */
    private UUID shortUrlId;

    /**
     * Each test starts from an empty stream. Deleting the key also drops the consumer group, so it
     * is recreated here rather than reaching into the service's startup hook.
     */
    @BeforeEach
    void resetStreamAndCreateUrl() throws Exception {
        redis.delete(AnalyticsRedisConstants.CLICK_STREAM_KEY);
        try {
            redis.opsForStream().createGroup(
                    AnalyticsRedisConstants.CLICK_STREAM_KEY,
                    ReadOffset.from("0"),
                    AnalyticsRedisConstants.CLICK_STREAM_GROUP);
        } catch (Exception alreadyExists) {
            // Another test in this class already recreated it.
        }

        String email = "stream-" + UUID.randomUUID() + "@example.com";
        registerUser(email, "StrongP@ss1", "Stream");
        String accessToken = login(email, "StrongP@ss1").accessToken();

        String createJson = mockMvc.perform(post("/api/v1/urls")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"originalUrl\": \"https://example.com/stream-lifecycle\" }"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        shortUrlId = UUID.fromString(JsonPath.read(createJson, "$.data.id"));
    }

    @Test
    void flushedEntriesAreRemovedFromTheStream() {
        appendClick(shortUrlId);
        appendClick(shortUrlId);

        flushService.flush();

        // Acknowledging alone leaves the entry in the stream forever. If this regresses, Redis
        // memory grows without bound for the lifetime of the deployment.
        assertEquals(0L, streamLength(),
                "Processed entries were left in the stream, so it would grow without bound");
    }

    @Test
    void entriesStrandedByADeadConsumerAreReclaimedAndPersisted() {
        appendClick(shortUrlId);

        // Read as a different consumer and never acknowledge: exactly what an instance that is
        // killed mid-flush leaves behind. Those entries are pending against a consumer that will
        // never come back, and reads of new entries never revisit them.
        readAsAbandonedConsumer();
        assertEquals(0, persistedClicks(shortUrlId),
                "Precondition: the abandoned read should not have persisted anything");

        flushService.flush();

        assertEquals(1, persistedClicks(shortUrlId),
                "The stranded click was never recovered, so it would be lost permanently");
        assertEquals(0L, streamLength());
    }

    @Test
    void idleConsumersWithNothingPendingAreRemoved() {
        appendClick(shortUrlId);
        readAsAbandonedConsumer();
        flushService.flush();

        // The abandoned consumer's entries were reclaimed above, so it now holds nothing and is
        // eligible for removal. Without this, every restart leaves a consumer behind for good.
        flushService.flush();

        boolean abandonedStillPresent = redis.opsForStream()
                .consumers(AnalyticsRedisConstants.CLICK_STREAM_KEY,
                        AnalyticsRedisConstants.CLICK_STREAM_GROUP)
                .stream()
                .anyMatch(consumer -> ABANDONED_CONSUMER.equals(consumer.consumerName()));

        assertTrue(!abandonedStillPresent,
                "Idle consumer was not removed; the group accumulates one per restart");
    }

    private static final String ABANDONED_CONSUMER = "flush-consumer-deadbeef";

    private void readAsAbandonedConsumer() {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(AnalyticsRedisConstants.CLICK_STREAM_GROUP, ABANDONED_CONSUMER),
                StreamReadOptions.empty().count(100),
                StreamOffset.create(AnalyticsRedisConstants.CLICK_STREAM_KEY, ReadOffset.lastConsumed()));

        assertTrue(records != null && !records.isEmpty(),
                "Precondition: the abandoned consumer should have taken delivery of the entry");
    }

    private int persistedClicks(UUID shortUrlId) {
        return clickEventRepository
                .findByShortUrlIdOrderByClickedAtDesc(shortUrlId, Pageable.ofSize(10))
                .size();
    }

    private void appendClick(UUID shortUrlId) {
        redis.opsForStream().add(MapRecord.create(
                AnalyticsRedisConstants.CLICK_STREAM_KEY,
                Map.of(
                        "shortUrlId", shortUrlId.toString(),
                        "ipAddress", "203.0.113.5",
                        "userAgent", "integration-test",
                        "referer", "",
                        "clickedAt", Instant.now().toString())));
    }

    private long streamLength() {
        Long size = redis.opsForStream().size(AnalyticsRedisConstants.CLICK_STREAM_KEY);
        return size == null ? 0L : size;
    }
}

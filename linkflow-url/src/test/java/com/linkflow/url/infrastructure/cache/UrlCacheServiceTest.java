package com.linkflow.url.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linkflow.url.domain.entity.ShortUrl;
import com.linkflow.url.infrastructure.cache.UrlCacheService.CachedUrlEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private UrlCacheService urlCacheService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        urlCacheService = new UrlCacheService(
                stringRedisTemplate, objectMapper, com.linkflow.common.metrics.LinkflowMetrics.noop());
    }

    @Test
    void get_returnsEmptyWhenCacheMiss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:shortcode:abc")).thenReturn(null);

        assertTrue(urlCacheService.get("abc").isEmpty());
    }

    @Test
    void put_storesJsonWithJitteredTtl() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        UUID id = UUID.randomUUID();
        ShortUrl shortUrl = ShortUrl.builder()
                .id(id)
                .shortCode("MyCode")
                .originalUrl("https://example.com")
                .active(true)
                .deleted(false)
                .build();

        urlCacheService.put(shortUrl);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), ttlCaptor.capture());

        assertEquals("url:shortcode:mycode", keyCaptor.getValue());

        // Redis TTL = 2 * BASE_TTL (30 min) ± 20% jitter = 1440s to 2160s
        long ttlSeconds = ttlCaptor.getValue().getSeconds();
        assertTrue(ttlSeconds >= 1440 && ttlSeconds <= 2160,
                "Expected TTL between 1440s and 2160s, got " + ttlSeconds);
    }

    @Test
    void get_deserializesCachedEntry() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        UUID id = UUID.randomUUID();
        Instant cachedAt = Instant.now();
        CachedUrlEntry entry = new CachedUrlEntry(id, "https://example.com", true, null, false, false, cachedAt);
        String json = objectMapper.writeValueAsString(entry);
        when(valueOperations.get("url:shortcode:test")).thenReturn(json);

        Optional<CachedUrlEntry> result = urlCacheService.get("test");
        assertTrue(result.isPresent());
        assertEquals(id, result.get().id());
        assertEquals("https://example.com", result.get().originalUrl());
        assertFalse(result.get().negative());
        assertNotNull(result.get().cachedAt());
    }

    @Test
    void evict_deletesCacheKey() {
        urlCacheService.evict("ToRemove");
        verify(stringRedisTemplate).delete("url:shortcode:toremove");
    }

    // --- Negative caching tests ---

    @Test
    void putNegative_storesSentinelWithShortTtl() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        urlCacheService.putNegative("notfound");

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq("url:shortcode:notfound"), jsonCaptor.capture(), ttlCaptor.capture());

        // Negative TTL = 90s ± 20% jitter = 72s to 108s
        long ttlSeconds = ttlCaptor.getValue().getSeconds();
        assertTrue(ttlSeconds >= 72 && ttlSeconds <= 108,
                "Expected negative TTL between 72s and 108s, got " + ttlSeconds);

        CachedUrlEntry parsed = objectMapper.readValue(jsonCaptor.getValue(), CachedUrlEntry.class);
        assertTrue(parsed.negative());
        assertNull(parsed.id());
    }

    @Test
    void get_returnsNegativeEntry() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        CachedUrlEntry negEntry = CachedUrlEntry.notFound();
        String json = objectMapper.writeValueAsString(negEntry);
        when(valueOperations.get("url:shortcode:bad")).thenReturn(json);

        Optional<CachedUrlEntry> result = urlCacheService.get("bad");
        assertTrue(result.isPresent());
        assertTrue(result.get().negative());
    }

    // --- Freshness / SWR tests ---

    @Test
    void isFresh_trueForRecentEntry() {
        CachedUrlEntry entry = new CachedUrlEntry(
                UUID.randomUUID(), "https://example.com", true, null, false,
                false, Instant.now());
        assertTrue(urlCacheService.isFresh(entry));
        assertFalse(urlCacheService.isStale(entry));
    }

    @Test
    void isStale_trueForOldEntry() {
        CachedUrlEntry entry = new CachedUrlEntry(
                UUID.randomUUID(), "https://example.com", true, null, false,
                false, Instant.now().minus(20, ChronoUnit.MINUTES));
        assertFalse(urlCacheService.isFresh(entry));
        assertTrue(urlCacheService.isStale(entry));
    }

    @Test
    void negativeEntry_neverStale() {
        // Even with old cachedAt, negative entries should never be "stale" (no SWR)
        CachedUrlEntry negEntry = new CachedUrlEntry(
                null, null, false, null, false,
                true, Instant.now().minus(20, ChronoUnit.MINUTES));
        assertTrue(urlCacheService.isFresh(negEntry));
        assertFalse(urlCacheService.isStale(negEntry));
    }

    // --- Jitter tests ---

    @Test
    void jitter_producesValueWithinRange() {
        Duration base = Duration.ofMinutes(15);
        // Run multiple times to check range
        for (int i = 0; i < 100; i++) {
            Duration jittered = UrlCacheService.jitter(base);
            long seconds = jittered.getSeconds();
            // 900 ± 20% = 720 to 1080
            assertTrue(seconds >= 720 && seconds <= 1080,
                    "Jittered value " + seconds + " outside range [720, 1080]");
        }
    }

    @Test
    void jitter_zeroBaseReturnsZero() {
        Duration zero = Duration.ZERO;
        assertEquals(Duration.ZERO, UrlCacheService.jitter(zero));
    }
}

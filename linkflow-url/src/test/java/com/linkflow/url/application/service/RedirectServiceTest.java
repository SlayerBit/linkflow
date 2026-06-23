package com.linkflow.url.application.service;

import com.linkflow.common.exception.ResourceNotFoundException;
import com.linkflow.common.port.ClickTrackingPort;
import com.linkflow.url.domain.entity.ShortUrl;
import com.linkflow.url.domain.exception.UrlDeactivatedException;
import com.linkflow.url.domain.exception.UrlExpiredException;
import com.linkflow.url.domain.repository.ShortUrlRepository;
import com.linkflow.url.infrastructure.cache.UrlCacheService;
import com.linkflow.url.infrastructure.cache.UrlCacheService.CachedUrlEntry;
import com.linkflow.url.infrastructure.lock.RedisLockService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @Mock
    private ShortUrlRepository shortUrlRepository;
    @Mock
    private UrlCacheService urlCacheService;
    @Mock
    private ClickTrackingPort clickTrackingPort;
    @Mock
    private RedisLockService redisLockService;
    @Mock
    private RedirectCacheRefreshService redirectCacheRefreshService;
    @Mock
    private HttpServletRequest request;

    private RedirectService redirectService;

    @BeforeEach
    void setUp() {
        redirectService = new RedirectService(
                shortUrlRepository, urlCacheService, clickTrackingPort,
                redisLockService, redirectCacheRefreshService);
    }

    // --- Fresh cache hit ---

    @Test
    void resolveRedirect_freshCacheHit_servesDirectly() {
        UUID id = UUID.randomUUID();
        CachedUrlEntry entry = new CachedUrlEntry(
                id, "https://cached.example.com", true, null, false, false, Instant.now());
        when(urlCacheService.get("abc")).thenReturn(Optional.of(entry));
        when(urlCacheService.isStale(entry)).thenReturn(false);

        String url = redirectService.resolveRedirect("ABC", request);

        assertEquals("https://cached.example.com", url);
        verify(clickTrackingPort).trackClick(any());
        verify(shortUrlRepository, never()).findByShortCode(any());
    }

    // --- Stale-while-revalidate ---

    @Test
    void resolveRedirect_staleCacheHit_servesStaleAndTriggersRefresh() {
        UUID id = UUID.randomUUID();
        CachedUrlEntry staleEntry = new CachedUrlEntry(
                id, "https://stale.example.com", true, null, false,
                false, Instant.now().minus(20, ChronoUnit.MINUTES));
        when(urlCacheService.get("swr")).thenReturn(Optional.of(staleEntry));
        when(urlCacheService.isStale(staleEntry)).thenReturn(true);

        String url = redirectService.resolveRedirect("swr", request);

        assertEquals("https://stale.example.com", url);
        verify(clickTrackingPort).trackClick(any());
        verify(redirectCacheRefreshService).refreshCacheEntry("swr");
        verify(shortUrlRepository, never()).findByShortCode(any());
    }

    // --- Negative cache ---

    @Test
    void resolveRedirect_negativeCacheHit_throwsNotFoundWithoutDb() {
        CachedUrlEntry negEntry = CachedUrlEntry.notFound();
        when(urlCacheService.get("bad")).thenReturn(Optional.of(negEntry));

        assertThrows(ResourceNotFoundException.class,
                () -> redirectService.resolveRedirect("bad", request));

        verify(shortUrlRepository, never()).findByShortCode(any());
    }

    // --- Cache miss with stampede protection ---

    @Test
    void resolveRedirect_cacheMiss_lockAcquired_fetchesFromDb() {
        UUID id = UUID.randomUUID();
        ShortUrl shortUrl = ShortUrl.builder()
                .id(id).shortCode("abc").originalUrl("https://db.example.com")
                .active(true).deleted(false).build();

        when(urlCacheService.get("abc")).thenReturn(Optional.empty());
        when(redisLockService.tryLock(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(shortUrlRepository.findByShortCode("abc")).thenReturn(Optional.of(shortUrl));

        String url = redirectService.resolveRedirect("abc", request);

        assertEquals("https://db.example.com", url);
        verify(urlCacheService).put(shortUrl);
        verify(redisLockService).unlock(anyString(), anyString());
    }

    @Test
    void resolveRedirect_cacheMiss_dbNotFound_storesNegativeCache() {
        when(urlCacheService.get("missing")).thenReturn(Optional.empty());
        when(redisLockService.tryLock(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> redirectService.resolveRedirect("missing", request));

        verify(urlCacheService).putNegative("missing");
    }

    // --- Expired / deactivated URLs ---

    @Test
    void resolveRedirect_expiredUrlThrowsGone() {
        CachedUrlEntry entry = new CachedUrlEntry(
                UUID.randomUUID(), "https://expired.example.com", true,
                Instant.now().minus(1, ChronoUnit.HOURS), false, false, Instant.now());
        when(urlCacheService.get("exp")).thenReturn(Optional.of(entry));

        assertThrows(UrlExpiredException.class,
                () -> redirectService.resolveRedirect("exp", request));
    }

    @Test
    void resolveRedirect_deactivatedUrlThrowsGone() {
        CachedUrlEntry entry = new CachedUrlEntry(
                UUID.randomUUID(), "https://inactive.example.com", false, null, false,
                false, Instant.now());
        when(urlCacheService.get("off")).thenReturn(Optional.of(entry));

        assertThrows(UrlDeactivatedException.class,
                () -> redirectService.resolveRedirect("off", request));
    }

    // --- Stampede: lock not acquired, retries exhausted ---

    @Test
    void resolveRedirect_cacheMiss_lockNotAcquired_retriesExhausted_fallsToDb() {
        UUID id = UUID.randomUUID();
        ShortUrl shortUrl = ShortUrl.builder()
                .id(id).shortCode("retry").originalUrl("https://fallback.example.com")
                .active(true).deleted(false).build();

        // First call: miss; all retry calls: miss; final fallback DB call
        when(urlCacheService.get("retry")).thenReturn(Optional.empty());
        when(redisLockService.tryLock(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false)  // initial lock attempt
                .thenReturn(true);  // fallback lock in fetchFromDbAndCache after retries
        when(shortUrlRepository.findByShortCode("retry")).thenReturn(Optional.of(shortUrl));

        String url = redirectService.resolveRedirect("retry", request);

        assertEquals("https://fallback.example.com", url);
    }

    // --- Click tracking failure does not break redirect ---

    @Test
    void resolveRedirect_clickTrackingFailure_doesNotBreakRedirect() {
        UUID id = UUID.randomUUID();
        CachedUrlEntry entry = new CachedUrlEntry(
                id, "https://example.com", true, null, false, false, Instant.now());
        when(urlCacheService.get("ok")).thenReturn(Optional.of(entry));
        doThrow(new RuntimeException("tracking failed"))
                .when(clickTrackingPort).trackClick(any());

        // Should still return URL despite tracking failure
        String url = redirectService.resolveRedirect("ok", request);
        assertEquals("https://example.com", url);
    }
}

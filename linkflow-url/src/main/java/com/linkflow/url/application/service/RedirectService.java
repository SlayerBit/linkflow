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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for resolving short URL redirects with production-grade caching.
 * <p>
 * Cache behavior:
 * <ul>
 *   <li><b>Cache hit (fresh):</b> Serve directly from cache</li>
 *   <li><b>Cache hit (stale, SWR):</b> Serve stale entry immediately, trigger async
 *       background refresh. Negative entries never participate in SWR.</li>
 *   <li><b>Cache hit (negative):</b> Immediately throw ResourceNotFoundException
 *       without DB access</li>
 *   <li><b>Cache miss:</b> Acquire refresh lock with bounded retries (stampede protection).
 *       One request fetches from DB and populates cache. Others retry cache read up to
 *       {@link #MAX_STAMPEDE_RETRIES} times with backoff. If retries exhausted, fall through
 *       to direct DB query.</li>
 * </ul>
 * <p>
 * Failure modes:
 * <ul>
 *   <li>Redis down: all requests go to DB (degraded but correct)</li>
 *   <li>Lock acquisition failure: fall through to DB query</li>
 *   <li>Background refresh failure: stale entry continues serving until Redis TTL expires</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    /** Maximum number of retry attempts when waiting for another request to populate cache. */
    private static final int MAX_STAMPEDE_RETRIES = 3;

    /** Backoff between stampede retry attempts. */
    private static final Duration STAMPEDE_RETRY_DELAY = Duration.ofMillis(100);

    /** TTL for the cache refresh lock (short, auto-expires if holder crashes). */
    private static final Duration CACHE_REFRESH_LOCK_TTL = Duration.ofSeconds(5);

    private static final String CACHE_REFRESH_LOCK_PREFIX = "cache_refresh:";

    private final ShortUrlRepository shortUrlRepository;
    private final UrlCacheService urlCacheService;
    private final ClickTrackingPort clickTrackingPort;
    private final RedisLockService redisLockService;

    @Transactional(readOnly = true)
    public String resolveRedirect(String shortCode, HttpServletRequest request) {
        String normalized = shortCode.toLowerCase();

        // --- Step 1: Check cache ---
        Optional<CachedUrlEntry> cached = urlCacheService.get(normalized);

        if (cached.isPresent()) {
            CachedUrlEntry entry = cached.get();

            // Negative cache hit: reject immediately
            if (entry.negative()) {
                throw new ResourceNotFoundException("Short URL", shortCode);
            }

            // Stale-while-revalidate: serve stale entry, trigger async refresh
            if (urlCacheService.isStale(entry)) {
                log.debug("Serving stale cache entry for shortCode={}, triggering async refresh", normalized);
                triggerAsyncRefresh(normalized);
            }

            validateRedirectable(entry.deleted(), entry.active(), entry.expiresAt(), normalized);
            trackClick(entry.id(), request);
            return entry.originalUrl();
        }

        // --- Step 2: Cache miss — stampede-protected DB fetch ---
        return resolveFromDbWithStampedeProtection(normalized, shortCode, request);
    }

    /**
     * Cache miss handler with stampede protection using bounded retries.
     * <p>
     * One request acquires a short-lived lock and fetches from DB. Others retry
     * the cache read up to MAX_STAMPEDE_RETRIES times. If retries are exhausted
     * (lock holder is slow or failed), fall through to direct DB query.
     */
    private String resolveFromDbWithStampedeProtection(String normalized, String shortCode,
                                                        HttpServletRequest request) {
        String lockName = CACHE_REFRESH_LOCK_PREFIX + normalized;
        String lockValue = UUID.randomUUID().toString();

        if (redisLockService.tryLock(lockName, lockValue, CACHE_REFRESH_LOCK_TTL)) {
            // This request is the cache populator
            try {
                return fetchFromDbAndCache(normalized, shortCode, request);
            } finally {
                redisLockService.unlock(lockName, lockValue);
            }
        }

        // Another request is populating — retry cache with bounded attempts
        for (int attempt = 1; attempt <= MAX_STAMPEDE_RETRIES; attempt++) {
            try {
                Thread.sleep(STAMPEDE_RETRY_DELAY.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }

            Optional<CachedUrlEntry> retried = urlCacheService.get(normalized);
            if (retried.isPresent()) {
                CachedUrlEntry entry = retried.get();
                if (entry.negative()) {
                    throw new ResourceNotFoundException("Short URL", shortCode);
                }
                validateRedirectable(entry.deleted(), entry.active(), entry.expiresAt(), normalized);
                trackClick(entry.id(), request);
                return entry.originalUrl();
            }
        }

        // Retries exhausted — fall through to direct DB query
        log.debug("Stampede retries exhausted for shortCode={}, falling through to DB", normalized);
        return fetchFromDbAndCache(normalized, shortCode, request);
    }

    /**
     * Fetch URL from database, populate cache, and return the original URL.
     * If not found in DB, store a negative cache entry.
     */
    private String fetchFromDbAndCache(String normalized, String shortCode,
                                        HttpServletRequest request) {
        Optional<ShortUrl> dbResult = shortUrlRepository.findByShortCode(normalized);

        if (dbResult.isEmpty()) {
            urlCacheService.putNegative(normalized);
            throw new ResourceNotFoundException("Short URL", shortCode);
        }

        ShortUrl shortUrl = dbResult.get();
        validateRedirectable(shortUrl.isDeleted(), shortUrl.isActive(), shortUrl.getExpiresAt(), normalized);

        urlCacheService.put(shortUrl);
        trackClick(shortUrl.getId(), request);

        return shortUrl.getOriginalUrl();
    }

    /**
     * Trigger an asynchronous background refresh of the cache entry.
     * If the refresh fails, the stale entry remains until Redis TTL expires.
     */
    @Async("clickTrackingExecutor")
    public void triggerAsyncRefresh(String shortCode) {
        String lockName = CACHE_REFRESH_LOCK_PREFIX + shortCode;
        String lockValue = UUID.randomUUID().toString();

        // Only one async refresh at a time per shortcode
        if (!redisLockService.tryLock(lockName, lockValue, CACHE_REFRESH_LOCK_TTL)) {
            return; // Another refresh is in progress
        }

        try {
            shortUrlRepository.findByShortCode(shortCode).ifPresentOrElse(
                    urlCacheService::put,
                    () -> urlCacheService.putNegative(shortCode)
            );
        } catch (Exception ex) {
            log.warn("Background cache refresh failed for shortCode={}: {}", shortCode, ex.getMessage());
        } finally {
            redisLockService.unlock(lockName, lockValue);
        }
    }

    private void validateRedirectable(boolean deleted, boolean active,
                                      java.time.Instant expiresAt, String shortCode) {
        if (deleted || !active) {
            throw new UrlDeactivatedException(shortCode);
        }
        if (expiresAt != null && expiresAt.isBefore(java.time.Instant.now())) {
            throw new UrlExpiredException(shortCode);
        }
    }

    private void trackClick(UUID shortUrlId, HttpServletRequest request) {
        try {
            clickTrackingPort.trackClick(new ClickTrackingPort.ClickTrackingCommand(
                    shortUrlId,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent"),
                    request.getHeader("Referer")
            ));
        } catch (Exception ex) {
            log.warn("Click tracking failed for shortUrlId={}: {}", shortUrlId, ex.getMessage());
        }
    }
}

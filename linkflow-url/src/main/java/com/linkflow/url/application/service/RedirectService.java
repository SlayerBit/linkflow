package com.linkflow.url.application.service;

import com.linkflow.common.exception.ResourceNotFoundException;
import com.linkflow.common.metrics.LinkflowMetrics;
import com.linkflow.common.port.ClickTrackingPort;
import com.linkflow.common.security.ClientIpResolver;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for resolving short URL redirects with production-grade caching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    private static final int MAX_STAMPEDE_RETRIES = 3;
    private static final Duration STAMPEDE_RETRY_DELAY = Duration.ofMillis(100);
    private static final Duration CACHE_REFRESH_LOCK_TTL = Duration.ofSeconds(5);
    private static final String CACHE_REFRESH_LOCK_PREFIX = "cache_refresh:";

    private final ShortUrlRepository shortUrlRepository;
    private final UrlCacheService urlCacheService;
    private final ClickTrackingPort clickTrackingPort;
    private final RedisLockService redisLockService;
    private final RedirectCacheRefreshService redirectCacheRefreshService;
    private final ClientIpResolver clientIpResolver;
    private final LinkflowMetrics metrics;

    @Transactional(readOnly = true)
    public String resolveRedirect(String shortCode, HttpServletRequest request) {
        try {
            return resolveRedirectInternal(shortCode, request);
        } catch (ResourceNotFoundException ex) {
            metrics.redirectRejected("not_found");
            throw ex;
        } catch (UrlExpiredException ex) {
            metrics.redirectRejected("expired");
            throw ex;
        } catch (UrlDeactivatedException ex) {
            metrics.redirectRejected("deactivated");
            throw ex;
        }
    }

    private String resolveRedirectInternal(String shortCode, HttpServletRequest request) {
        String normalized = shortCode.toLowerCase();

        Optional<CachedUrlEntry> cached = urlCacheService.get(normalized);

        if (cached.isPresent()) {
            CachedUrlEntry entry = cached.get();

            if (entry.negative()) {
                throw new ResourceNotFoundException("Short URL", shortCode);
            }

            String cacheOutcome = "hit";
            if (urlCacheService.isStale(entry)) {
                log.debug("Serving stale cache entry for shortCode={}, triggering async refresh", normalized);
                redirectCacheRefreshService.refreshCacheEntry(normalized);
                cacheOutcome = "stale";
            }

            validateRedirectable(entry.deleted(), entry.active(), entry.expiresAt(), normalized);
            trackClick(entry.id(), request);
            metrics.redirectResolved(cacheOutcome);
            return entry.originalUrl();
        }

        return resolveFromDbWithStampedeProtection(normalized, shortCode, request);
    }

    private String resolveFromDbWithStampedeProtection(String normalized, String shortCode,
                                                        HttpServletRequest request) {
        String lockName = CACHE_REFRESH_LOCK_PREFIX + normalized;
        String lockValue = UUID.randomUUID().toString();

        if (redisLockService.tryLock(lockName, lockValue, CACHE_REFRESH_LOCK_TTL)) {
            try {
                return fetchFromDbAndCache(normalized, shortCode, request);
            } finally {
                redisLockService.unlock(lockName, lockValue);
            }
        }

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
                metrics.redirectResolved("hit");
                return entry.originalUrl();
            }
        }

        log.debug("Stampede retries exhausted for shortCode={}, falling through to DB", normalized);
        return fetchFromDbAndCache(normalized, shortCode, request);
    }

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
        metrics.redirectResolved("miss");

        return shortUrl.getOriginalUrl();
    }

    private void validateRedirectable(boolean deleted, boolean active,
                                      Instant expiresAt, String shortCode) {
        if (deleted) {
            throw new UrlDeactivatedException(shortCode);
        }
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new UrlExpiredException(shortCode);
        }
        if (!active) {
            throw new UrlDeactivatedException(shortCode);
        }
    }

    private void trackClick(UUID shortUrlId, HttpServletRequest request) {
        try {
            clickTrackingPort.trackClick(new ClickTrackingPort.ClickTrackingCommand(
                    shortUrlId,
                    // Behind a reverse proxy getRemoteAddr() is the proxy, which would collapse
                    // every visitor into one address and make unique-visitor counts meaningless.
                    clientIpResolver.resolve(request),
                    request.getHeader("User-Agent"),
                    request.getHeader("Referer")
            ));
        } catch (Exception ex) {
            log.warn("Click tracking failed for shortUrlId={}: {}", shortUrlId, ex.getMessage());
        }
    }
}

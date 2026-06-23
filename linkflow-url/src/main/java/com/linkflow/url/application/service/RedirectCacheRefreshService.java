package com.linkflow.url.application.service;

import com.linkflow.url.domain.repository.ShortUrlRepository;
import com.linkflow.url.infrastructure.cache.UrlCacheService;
import com.linkflow.url.infrastructure.lock.RedisLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Async cache refresh worker extracted from {@link RedirectService} so {@code @Async}
 * is applied through the Spring proxy instead of self-invocation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectCacheRefreshService {

    private static final Duration CACHE_REFRESH_LOCK_TTL = Duration.ofSeconds(5);
    private static final String CACHE_REFRESH_LOCK_PREFIX = "cache_refresh:";

    private final ShortUrlRepository shortUrlRepository;
    private final UrlCacheService urlCacheService;
    private final RedisLockService redisLockService;

    @Async("clickTrackingExecutor")
    public void refreshCacheEntry(String shortCode) {
        String lockName = CACHE_REFRESH_LOCK_PREFIX + shortCode;
        String lockValue = UUID.randomUUID().toString();

        if (!redisLockService.tryLock(lockName, lockValue, CACHE_REFRESH_LOCK_TTL)) {
            return;
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
}

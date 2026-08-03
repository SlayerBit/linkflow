package com.linkflow.url.infrastructure.cache;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkflow.common.metrics.LinkflowMetrics;
import com.linkflow.url.domain.entity.ShortUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis cache-aside service for URL redirect lookups.
 * <p>
 * Features:
 * <ul>
 *   <li><b>TTL jitter:</b> ±20% randomization on all TTLs to prevent cache stampede
 *       from synchronized expiry</li>
 *   <li><b>Negative caching:</b> Sentinel entries for missing shortcodes (90s TTL)
 *       to avoid repeated DB lookups for invalid codes</li>
 *   <li><b>Stale-while-revalidate (SWR):</b> Entries store a {@code cachedAt} timestamp.
 *       After the base TTL (fresh window), entries enter a stale window where they can
 *       still be served while a background refresh repopulates the cache. Negative entries
 *       never participate in SWR — they are either fresh or expired.</li>
 * </ul>
 * <p>
 * Redis TTL is set to {@code baseTTL * 2} (with jitter) to support the stale window.
 * The actual freshness is determined by comparing {@code cachedAt + baseTTL} to now.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCacheService {

    private static final String KEY_PREFIX = "url:shortcode:";

    /** Base TTL for positive entries — entries are "fresh" for this duration. */
    static final Duration BASE_TTL = Duration.ofMinutes(15);

    /** Redis TTL for positive entries — 2x base TTL to support SWR stale window. */
    private static final Duration REDIS_TTL = BASE_TTL.multipliedBy(2);

    /** Base TTL for negative (not-found) sentinel entries. Short to limit staleness. */
    static final Duration NEGATIVE_TTL = Duration.ofSeconds(90);

    /** Jitter factor: ±20% of the base TTL. */
    private static final double JITTER_FACTOR = 0.20;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final LinkflowMetrics metrics;

    /**
     * Get a cached entry for the given shortcode.
     *
     * @return the cached entry if present, empty if cache miss
     */
    public Optional<CachedUrlEntry> get(String shortCode) {
        String key = cacheKey(shortCode);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                metrics.urlCacheLookup("miss");
                return Optional.empty();
            }
            CachedUrlEntry entry = objectMapper.readValue(json, CachedUrlEntry.class);
            metrics.urlCacheLookup(entry.negative() ? "negative" : "hit");
            return Optional.of(entry);
        } catch (Exception ex) {
            log.warn("Failed to read URL cache for shortCode={}: {}", shortCode, ex.getMessage());
            metrics.urlCacheLookup("error");
            return Optional.empty();
        }
    }

    /**
     * Cache a positive entry for a ShortUrl. Uses extended Redis TTL to support SWR.
     */
    public void put(ShortUrl shortUrl) {
        put(shortUrl.getShortCode(), toEntry(shortUrl));
    }

    /**
     * Cache a positive entry with SWR support.
     */
    public void put(String shortCode, CachedUrlEntry entry) {
        String key = cacheKey(shortCode);
        try {
            String json = objectMapper.writeValueAsString(entry);
            Duration jitteredTtl = jitter(REDIS_TTL);
            stringRedisTemplate.opsForValue().set(key, json, jitteredTtl);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to write URL cache for shortCode={}: {}", shortCode, ex.getMessage());
        }
    }

    /**
     * Cache a negative (not-found) sentinel entry. Negative entries have a short TTL
     * and never participate in stale-while-revalidate.
     */
    public void putNegative(String shortCode) {
        String key = cacheKey(shortCode);
        try {
            CachedUrlEntry negativeEntry = CachedUrlEntry.notFound();
            String json = objectMapper.writeValueAsString(negativeEntry);
            Duration jitteredTtl = jitter(NEGATIVE_TTL);
            stringRedisTemplate.opsForValue().set(key, json, jitteredTtl);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to write negative cache for shortCode={}: {}", shortCode, ex.getMessage());
        }
    }

    /**
     * Evict a cached entry (positive or negative). Called on URL mutation
     * (update, delete, deactivate) to ensure correctness.
     */
    public void evict(String shortCode) {
        try {
            stringRedisTemplate.delete(cacheKey(shortCode));
        } catch (Exception ex) {
            log.warn("Failed to evict URL cache for shortCode={}: {}", shortCode, ex.getMessage());
        }
    }

    /**
     * Check if an entry is still within the fresh window (cachedAt + BASE_TTL > now).
     * Negative entries are never considered stale — they are either fresh or expired
     * (handled by Redis TTL).
     */
    public boolean isFresh(CachedUrlEntry entry) {
        if (entry.negative()) {
            return true; // Negative entries are always treated as fresh (no SWR)
        }
        if (entry.cachedAt() == null) {
            return true; // Defensive: treat entries without cachedAt as fresh
        }
        return Instant.now().isBefore(entry.cachedAt().plus(BASE_TTL));
    }

    /**
     * Check if an entry is in the stale window (past fresh, but still in Redis).
     * Negative entries never participate in SWR.
     */
    public boolean isStale(CachedUrlEntry entry) {
        if (entry.negative()) {
            return false; // Negative entries never participate in SWR
        }
        return !isFresh(entry);
    }

    private String cacheKey(String shortCode) {
        return KEY_PREFIX + shortCode.toLowerCase();
    }

    private CachedUrlEntry toEntry(ShortUrl shortUrl) {
        return new CachedUrlEntry(
                shortUrl.getId(),
                shortUrl.getOriginalUrl(),
                shortUrl.isActive(),
                shortUrl.getExpiresAt(),
                shortUrl.isDeleted(),
                false,
                Instant.now()
        );
    }

    /**
     * Apply ±20% jitter to a Duration to prevent synchronized cache expiry.
     */
    static Duration jitter(Duration base) {
        long baseSeconds = base.getSeconds();
        long jitterRange = (long) (baseSeconds * JITTER_FACTOR);
        if (jitterRange == 0) {
            return base;
        }
        long offset = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        return Duration.ofSeconds(baseSeconds + offset);
    }

    /**
     * Cached representation of a ShortUrl for redirect resolution.
     *
     * @param id          URL entity ID (null for negative entries)
     * @param originalUrl target URL (null for negative entries)
     * @param active      whether the URL is active
     * @param expiresAt   URL expiration time (null = no expiry)
     * @param deleted     whether the URL is soft-deleted
     * @param negative    true if this is a not-found sentinel entry
     * @param cachedAt    timestamp when this entry was cached (for SWR freshness)
     */
    public record CachedUrlEntry(
            @JsonProperty("id") UUID id,
            @JsonProperty("originalUrl") String originalUrl,
            @JsonProperty("active") boolean active,
            @JsonProperty("expiresAt") Instant expiresAt,
            @JsonProperty("deleted") boolean deleted,
            @JsonProperty("negative") boolean negative,
            @JsonProperty("cachedAt") Instant cachedAt
    ) {
        @JsonCreator
        public CachedUrlEntry {
        }

        /** Create a negative (not-found) sentinel entry. */
        public static CachedUrlEntry notFound() {
            return new CachedUrlEntry(null, null, false, null, false, true, Instant.now());
        }
    }
}

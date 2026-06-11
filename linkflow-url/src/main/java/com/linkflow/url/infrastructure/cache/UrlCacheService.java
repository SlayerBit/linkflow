package com.linkflow.url.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkflow.url.domain.entity.ShortUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCacheService {

    private static final String KEY_PREFIX = "url:shortcode:";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<CachedUrlEntry> get(String shortCode) {
        String key = cacheKey(shortCode);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CachedUrlEntry.class));
        } catch (Exception ex) {
            log.warn("Failed to read URL cache for shortCode={}: {}", shortCode, ex.getMessage());
            return Optional.empty();
        }
    }

    public void put(ShortUrl shortUrl) {
        put(shortUrl.getShortCode(), toEntry(shortUrl));
    }

    public void put(String shortCode, CachedUrlEntry entry) {
        String key = cacheKey(shortCode);
        try {
            String json = objectMapper.writeValueAsString(entry);
            stringRedisTemplate.opsForValue().set(key, json, TTL);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to write URL cache for shortCode={}: {}", shortCode, ex.getMessage());
        }
    }

    public void evict(String shortCode) {
        try {
            stringRedisTemplate.delete(cacheKey(shortCode));
        } catch (Exception ex) {
            log.warn("Failed to evict URL cache for shortCode={}: {}", shortCode, ex.getMessage());
        }
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
                shortUrl.isDeleted()
        );
    }

    public record CachedUrlEntry(
            java.util.UUID id,
            String originalUrl,
            boolean active,
            Instant expiresAt,
            boolean deleted
    ) {}
}

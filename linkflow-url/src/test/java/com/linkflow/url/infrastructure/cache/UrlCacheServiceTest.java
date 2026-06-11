package com.linkflow.url.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @BeforeEach
    void setUp() {
        urlCacheService = new UrlCacheService(stringRedisTemplate, new ObjectMapper());
    }

    @Test
    void get_returnsEmptyWhenCacheMiss() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:shortcode:abc")).thenReturn(null);

        assertTrue(urlCacheService.get("abc").isEmpty());
    }

    @Test
    void put_storesJsonWithFifteenMinuteTtl() throws Exception {
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

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), eq(Duration.ofMinutes(15)));

        assertEquals("url:shortcode:mycode", keyCaptor.getValue());
    }

    @Test
    void get_deserializesCachedEntry() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        UUID id = UUID.randomUUID();
        CachedUrlEntry entry = new CachedUrlEntry(id, "https://example.com", true, null, false);
        String json = new ObjectMapper().writeValueAsString(entry);
        when(valueOperations.get("url:shortcode:test")).thenReturn(json);

        Optional<CachedUrlEntry> result = urlCacheService.get("test");
        assertTrue(result.isPresent());
        assertEquals(id, result.get().id());
        assertEquals("https://example.com", result.get().originalUrl());
    }

    @Test
    void evict_deletesCacheKey() {
        urlCacheService.evict("ToRemove");
        verify(stringRedisTemplate).delete("url:shortcode:toremove");
    }
}

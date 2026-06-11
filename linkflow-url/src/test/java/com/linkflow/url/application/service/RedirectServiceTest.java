package com.linkflow.url.application.service;

import com.linkflow.common.exception.ResourceNotFoundException;
import com.linkflow.common.port.ClickTrackingPort;
import com.linkflow.url.domain.entity.ShortUrl;
import com.linkflow.url.domain.exception.UrlDeactivatedException;
import com.linkflow.url.domain.exception.UrlExpiredException;
import com.linkflow.url.domain.repository.ShortUrlRepository;
import com.linkflow.url.infrastructure.cache.UrlCacheService;
import com.linkflow.url.infrastructure.cache.UrlCacheService.CachedUrlEntry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private HttpServletRequest request;

    private RedirectService redirectService;

    @BeforeEach
    void setUp() {
        redirectService = new RedirectService(shortUrlRepository, urlCacheService, clickTrackingPort);
    }

    @Test
    void resolveRedirect_usesCacheHit() {
        UUID id = UUID.randomUUID();
        CachedUrlEntry entry = new CachedUrlEntry(id, "https://cached.example.com", true, null, false);
        when(urlCacheService.get("abc")).thenReturn(Optional.of(entry));

        String url = redirectService.resolveRedirect("ABC", request);

        assertEquals("https://cached.example.com", url);
        verify(clickTrackingPort).trackClick(any());
        verify(shortUrlRepository, never()).findByShortCode(any());
    }

    @Test
    void resolveRedirect_loadsFromDbOnCacheMiss() {
        UUID id = UUID.randomUUID();
        ShortUrl shortUrl = ShortUrl.builder()
                .id(id)
                .shortCode("abc")
                .originalUrl("https://db.example.com")
                .active(true)
                .deleted(false)
                .build();

        when(urlCacheService.get("abc")).thenReturn(Optional.empty());
        when(shortUrlRepository.findByShortCode("abc")).thenReturn(Optional.of(shortUrl));

        String url = redirectService.resolveRedirect("abc", request);

        assertEquals("https://db.example.com", url);
        verify(urlCacheService).put(shortUrl);
    }

    @Test
    void resolveRedirect_expiredUrlThrowsGone() {
        CachedUrlEntry entry = new CachedUrlEntry(
                UUID.randomUUID(), "https://expired.example.com", true,
                Instant.now().minus(1, ChronoUnit.HOURS), false);
        when(urlCacheService.get("exp")).thenReturn(Optional.of(entry));

        assertThrows(UrlExpiredException.class, () -> redirectService.resolveRedirect("exp", request));
    }

    @Test
    void resolveRedirect_deactivatedUrlThrowsGone() {
        CachedUrlEntry entry = new CachedUrlEntry(
                UUID.randomUUID(), "https://inactive.example.com", false, null, false);
        when(urlCacheService.get("off")).thenReturn(Optional.of(entry));

        assertThrows(UrlDeactivatedException.class, () -> redirectService.resolveRedirect("off", request));
    }

    @Test
    void resolveRedirect_unknownCodeThrowsNotFound() {
        when(urlCacheService.get("missing")).thenReturn(Optional.empty());
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> redirectService.resolveRedirect("missing", request));
    }
}

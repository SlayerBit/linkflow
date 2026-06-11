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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedirectService {

    private final ShortUrlRepository shortUrlRepository;
    private final UrlCacheService urlCacheService;
    private final ClickTrackingPort clickTrackingPort;

    @Transactional(readOnly = true)
    public String resolveRedirect(String shortCode, HttpServletRequest request) {
        String normalized = shortCode.toLowerCase();

        Optional<CachedUrlEntry> cached = urlCacheService.get(normalized);
        if (cached.isPresent()) {
            CachedUrlEntry entry = cached.get();
            validateRedirectable(entry.deleted(), entry.active(), entry.expiresAt(), normalized);
            trackClick(entry.id(), request);
            return entry.originalUrl();
        }

        ShortUrl shortUrl = shortUrlRepository.findByShortCode(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", shortCode));

        validateRedirectable(shortUrl.isDeleted(), shortUrl.isActive(), shortUrl.getExpiresAt(), normalized);

        urlCacheService.put(shortUrl);
        trackClick(shortUrl.getId(), request);

        return shortUrl.getOriginalUrl();
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

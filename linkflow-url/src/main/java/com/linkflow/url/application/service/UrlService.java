package com.linkflow.url.application.service;

import com.linkflow.common.exception.ResourceNotFoundException;
import com.linkflow.common.metrics.LinkflowMetrics;
import com.linkflow.common.security.UserPrincipal;
import com.linkflow.url.api.dto.*;
import com.linkflow.url.domain.entity.ShortUrl;
import com.linkflow.url.domain.exception.AliasCollisionException;
import com.linkflow.url.domain.exception.InvalidUrlException;
import com.linkflow.url.domain.repository.IdempotencyRecordRepository;
import com.linkflow.url.domain.event.UrlMutatedEvent;
import com.linkflow.url.domain.repository.ShortUrlRepository;
import com.linkflow.url.infrastructure.config.UrlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlService {

    private static final String CREATE_ENDPOINT = "/api/v1/urls";
    private static final String BULK_CREATE_ENDPOINT = "/api/v1/urls/bulk";
    private static final String ALIAS_PATTERN = "^[a-zA-Z0-9_-]+$";

    private final ShortUrlRepository shortUrlRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final IdempotencyService idempotencyService;
    private final QrCodeService qrCodeService;
    private final UrlProperties urlProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final LinkflowMetrics metrics;

    @Transactional
    public UrlResponse createUrl(CreateUrlRequest request, String idempotencyKey) {
        UserPrincipal principal = getCurrentPrincipal();

        if (StringUtils.hasText(idempotencyKey)) {
            String requestHash = idempotencyService.hashRequestBody(request);
            var cached = idempotencyService.findCached(
                    principal.getId(), CREATE_ENDPOINT, idempotencyKey, requestHash, UrlResponse.class);
            if (cached.isPresent()) {
                return cached.get().body();
            }
        }

        ShortUrl shortUrl = persistUrl(request, principal.getId());
        UrlResponse response = toResponse(shortUrl);

        if (StringUtils.hasText(idempotencyKey)) {
            storeIdempotentResult(
                    principal.getId(), CREATE_ENDPOINT, idempotencyKey,
                    idempotencyService.hashRequestBody(request), 201, response);
        }

        metrics.urlsCreated(1, StringUtils.hasText(request.getCustomAlias()));
        log.info("Short URL created: id={}, shortCode={}, ownerId={}",
                shortUrl.getId(), shortUrl.getShortCode(), principal.getId());
        return response;
    }

    @Transactional
    public BulkCreateUrlResponse bulkCreateUrls(BulkCreateUrlRequest request, String idempotencyKey) {
        UserPrincipal principal = getCurrentPrincipal();

        var cached = idempotencyService.findCached(
                principal.getId(), BULK_CREATE_ENDPOINT, idempotencyKey,
                idempotencyService.hashRequestBody(request), BulkCreateUrlResponse.class);
        if (cached.isPresent()) {
            return cached.get().body();
        }

        validateBulkRequest(request);

        List<UrlResponse> responses = request.getUrls().stream()
                .map(item -> toResponse(persistUrl(item, principal.getId())))
                .toList();

        BulkCreateUrlResponse response = BulkCreateUrlResponse.builder()
                .urls(responses)
                .count(responses.size())
                .build();

        storeIdempotentResult(
                principal.getId(), BULK_CREATE_ENDPOINT, idempotencyKey,
                idempotencyService.hashRequestBody(request), 201, response);

        long customAliases = request.getUrls().stream()
                .filter(item -> StringUtils.hasText(item.getCustomAlias()))
                .count();
        if (customAliases > 0) {
            metrics.urlsCreated((int) customAliases, true);
        }
        int generated = responses.size() - (int) customAliases;
        if (generated > 0) {
            metrics.urlsCreated(generated, false);
        }

        log.info("Bulk short URLs created: count={}, ownerId={}", responses.size(), principal.getId());
        return response;
    }

    @Transactional(readOnly = true)
    public Page<UrlResponse> listUserUrls(Pageable pageable) {
        UserPrincipal principal = getCurrentPrincipal();
        return shortUrlRepository.findByOwnerIdAndNotDeleted(principal.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UrlResponse getUrlById(UUID id) {
        ShortUrl shortUrl = findOwnedUrl(id);
        return toResponse(shortUrl);
    }

    @Transactional(readOnly = true)
    public UrlResponse getUrlByIdAsAdmin(UUID id) {
        ShortUrl shortUrl = shortUrlRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", id.toString()));
        return toResponse(shortUrl);
    }

    @Transactional
    public UrlResponse updateUrl(UUID id, UpdateUrlRequest request) {
        ShortUrl shortUrl = findOwnedUrl(id);

        if (request.getExpiresAt() != null) {
            validateExpiresAt(request.getExpiresAt());
            shortUrl.setExpiresAt(request.getExpiresAt());
        }
        if (request.getActive() != null) {
            shortUrl.setActive(request.getActive());
        }

        shortUrl = shortUrlRepository.save(shortUrl);
        invalidateCaches(shortUrl.getShortCode());
        log.info("Short URL updated: id={}", shortUrl.getId());
        return toResponse(shortUrl);
    }

    @Transactional
    public void deleteUrl(UUID id) {
        ShortUrl shortUrl = findOwnedUrl(id);
        shortUrl.softDelete();
        shortUrlRepository.save(shortUrl);
        invalidateCaches(shortUrl.getShortCode());
        log.info("Short URL soft-deleted: id={}", id);
    }

    @Transactional(readOnly = true)
    public byte[] generateQrCodeAsAdmin(UUID id) {
        ShortUrl shortUrl = shortUrlRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", id.toString()));
        return qrCodeService.generatePng(shortUrl.getShortCode());
    }

    @Transactional(readOnly = true)
    public byte[] generateQrCode(UUID id) {
        ShortUrl shortUrl = findOwnedUrl(id);
        return qrCodeService.generatePng(shortUrl.getShortCode());
    }

    @Transactional(readOnly = true)
    public Page<UrlResponse> listAllUrls(Pageable pageable) {
        return shortUrlRepository.findAllNotDeleted(pageable)
                .map(this::toResponse);
    }

    @Transactional
    public UrlResponse adminDeactivateUrl(UUID id) {
        ShortUrl shortUrl = shortUrlRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", id.toString()));
        shortUrl.setActive(false);
        shortUrl = shortUrlRepository.save(shortUrl);
        invalidateCaches(shortUrl.getShortCode());
        log.info("Short URL deactivated by admin: id={}", id);
        return toResponse(shortUrl);
    }

    @Transactional
    public UrlResponse adminReactivateUrl(UUID id) {
        ShortUrl shortUrl = shortUrlRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", id.toString()));
        if (shortUrl.isExpired()) {
            throw new InvalidUrlException("Cannot reactivate an expired URL");
        }
        shortUrl.setActive(true);
        shortUrl = shortUrlRepository.save(shortUrl);
        invalidateCaches(shortUrl.getShortCode());
        log.info("Short URL reactivated by admin: id={}", id);
        return toResponse(shortUrl);
    }

    @Transactional
    public UrlResponse reactivateUrl(UUID id) {
        ShortUrl shortUrl = findOwnedUrl(id);
        if (shortUrl.isExpired()) {
            throw new InvalidUrlException("Cannot reactivate an expired URL");
        }
        shortUrl.setActive(true);
        shortUrl = shortUrlRepository.save(shortUrl);
        invalidateCaches(shortUrl.getShortCode());
        log.info("Short URL reactivated: id={}", id);
        return toResponse(shortUrl);
    }

    @Transactional
    public int deactivateExpiredUrls() {
        List<ShortUrl> expired = shortUrlRepository.findExpiredActive(Instant.now());
        for (ShortUrl shortUrl : expired) {
            shortUrl.setActive(false);
            invalidateCaches(shortUrl.getShortCode());
        }
        if (!expired.isEmpty()) {
            shortUrlRepository.saveAll(expired);
            log.info("Deactivated {} expired short URLs", expired.size());
        }
        return expired.size();
    }

    @Transactional
    public int cleanupExpiredIdempotencyRecords() {
        int deleted = idempotencyRecordRepository.deleteByExpiresAtBefore(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency records", deleted);
        }
        return deleted;
    }

    private ShortUrl persistUrl(CreateUrlRequest request, UUID ownerId) {
        validateCreateRequest(request);
        String shortCode = resolveShortCode(request.getCustomAlias());
        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode(shortCode)
                .originalUrl(request.getOriginalUrl().trim())
                .customAlias(request.getCustomAlias() != null ? shortCode : null)
                .ownerId(ownerId)
                .expiresAt(request.getExpiresAt())
                .build();

        ShortUrl saved = insertReportingCollisions(shortUrl);

        // Clears any not-found sentinel so the new link resolves immediately. Deferred to commit,
        // otherwise a concurrent redirect can re-cache the sentinel before the insert is visible.
        invalidateCaches(saved.getShortCode());

        return saved;
    }

    /**
     * Inserts the row, flushing so that a duplicate short code is reported as a conflict rather
     * than escaping as an unhandled failure at commit time.
     * <p>
     * The unique index on {@code short_code} is what actually guarantees uniqueness: the preceding
     * availability check can always be overtaken by a concurrent transaction that has not yet
     * committed. Hibernate would otherwise defer this insert to the end of the transaction, well
     * outside any handler here, turning a losing race into a 500 instead of a 409.
     */
    private ShortUrl insertReportingCollisions(ShortUrl shortUrl) {
        try {
            return shortUrlRepository.saveAndFlush(shortUrl);
        } catch (DataIntegrityViolationException ex) {
            throw new AliasCollisionException(shortUrl.getShortCode());
        }
    }

    /**
     * Picks the short code to use, rejecting an alias that is already taken.
     * <p>
     * This is a fast, friendly check, not the uniqueness guarantee — see
     * {@link #insertReportingCollisions}. It deliberately takes no distributed lock: a lock held
     * only for the duration of this check is released before the insert commits, so it excludes
     * nothing, and treating an unreachable Redis as a failed acquisition would reject every custom
     * alias with a bogus "already taken" error during a cache outage.
     */
    private String resolveShortCode(String customAlias) {
        if (!StringUtils.hasText(customAlias)) {
            return shortCodeGenerator.generate();
        }

        String normalized = customAlias.toLowerCase();
        validateAlias(normalized);

        if (shortUrlRepository.existsByShortCode(normalized)) {
            throw new AliasCollisionException(normalized);
        }
        return normalized;
    }

    private void validateBulkRequest(BulkCreateUrlRequest request) {
        Set<String> aliases = new HashSet<>();
        for (CreateUrlRequest item : request.getUrls()) {
            validateCreateRequest(item);
            if (StringUtils.hasText(item.getCustomAlias())) {
                String normalized = item.getCustomAlias().toLowerCase();
                if (!aliases.add(normalized)) {
                    throw new AliasCollisionException(normalized);
                }
                if (shortUrlRepository.existsByShortCode(normalized)) {
                    throw new AliasCollisionException(normalized);
                }
            }
        }
    }

    private void validateCreateRequest(CreateUrlRequest request) {
        validateOriginalUrl(request.getOriginalUrl());
        if (request.getExpiresAt() != null) {
            validateExpiresAt(request.getExpiresAt());
        }
        if (StringUtils.hasText(request.getCustomAlias())) {
            validateAlias(request.getCustomAlias().toLowerCase());
        }
    }

    private void validateOriginalUrl(String originalUrl) {
        if (!StringUtils.hasText(originalUrl)) {
            throw new InvalidUrlException("Original URL is required");
        }
        String trimmed = originalUrl.trim();
        if (trimmed.length() > 2048) {
            throw new InvalidUrlException("Original URL must not exceed 2048 characters");
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new InvalidUrlException("Original URL must use http or https scheme");
            }
            if (!StringUtils.hasText(uri.getHost())) {
                throw new InvalidUrlException("Original URL must have a valid host");
            }
        } catch (IllegalArgumentException ex) {
            throw new InvalidUrlException("Original URL is not valid: " + trimmed);
        }
    }

    private void validateExpiresAt(Instant expiresAt) {
        if (!expiresAt.isAfter(Instant.now())) {
            throw new InvalidUrlException("Expiration date must be in the future");
        }
    }

    private void validateAlias(String alias) {
        if (!alias.matches(ALIAS_PATTERN)) {
            throw new InvalidUrlException(
                    "Custom alias may only contain alphanumeric characters, hyphens, and underscores");
        }
    }

    private ShortUrl findOwnedUrl(UUID id) {
        UserPrincipal principal = getCurrentPrincipal();
        ShortUrl shortUrl = shortUrlRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", id.toString()));
        if (!shortUrl.getOwnerId().equals(principal.getId())) {
            throw new AccessDeniedException("You do not own this short URL");
        }
        return shortUrl;
    }

    /**
     * Requests cache eviction for a short code. The eviction itself is deferred until the
     * surrounding transaction commits — see {@code UrlCacheInvalidationListener} for why evicting
     * before commit reintroduces the staleness it is meant to prevent.
     */
    private void invalidateCaches(String shortCode) {
        eventPublisher.publishEvent(new UrlMutatedEvent(shortCode));
    }

    private UrlResponse toResponse(ShortUrl shortUrl) {
        return UrlResponse.builder()
                .id(shortUrl.getId())
                .shortCode(shortUrl.getShortCode())
                .shortUrl(buildShortUrl(shortUrl.getShortCode()))
                .originalUrl(shortUrl.getOriginalUrl())
                .expiresAt(shortUrl.getExpiresAt())
                .active(shortUrl.isActive())
                .createdAt(shortUrl.getCreatedAt())
                .build();
    }

    private String buildShortUrl(String shortCode) {
        String baseUrl = urlProperties.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/r/" + shortCode;
    }

    private UserPrincipal getCurrentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    private void storeIdempotentResult(UUID userId, String endpoint, String idempotencyKey,
                                       String requestHash, int statusCode, Object responseBody) {
        try {
            idempotencyService.store(userId, endpoint, idempotencyKey, requestHash, statusCode, responseBody);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            log.debug("Idempotency record already stored concurrently for key={}", idempotencyKey);
        }
    }
}

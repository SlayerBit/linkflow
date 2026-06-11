package com.linkflow.analytics.application.service;

import com.linkflow.analytics.api.dto.ClickEventResponse;
import com.linkflow.analytics.api.dto.SystemStatsResponse;
import com.linkflow.analytics.api.dto.TopUrlResponse;
import com.linkflow.analytics.api.dto.UrlAnalyticsResponse;
import com.linkflow.analytics.domain.entity.ClickEvent;
import com.linkflow.analytics.domain.entity.UrlAnalytics;
import com.linkflow.analytics.domain.repository.ClickEventRepository;
import com.linkflow.analytics.domain.repository.StatsRepository;
import com.linkflow.analytics.domain.repository.UrlAnalyticsRepository;
import com.linkflow.analytics.domain.repository.projection.TopUrlProjection;
import com.linkflow.common.exception.ResourceNotFoundException;
import com.linkflow.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private final UrlAnalyticsRepository urlAnalyticsRepository;
    private final ClickEventRepository clickEventRepository;
    private final StatsRepository statsRepository;

    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getUrlAnalytics(UUID shortUrlId) {
        UUID ownerId = urlAnalyticsRepository.findOwnerIdByShortUrlId(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", shortUrlId.toString()));

        UUID currentUserId = getCurrentPrincipal().getId();
        if (!ownerId.equals(currentUserId)) {
            throw new AccessDeniedException("You do not own this URL");
        }

        String shortCode = urlAnalyticsRepository.findShortCodeByShortUrlId(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", shortUrlId.toString()));

        UrlAnalytics analytics = urlAnalyticsRepository.findByShortUrlId(shortUrlId)
                .orElse(UrlAnalytics.builder()
                        .shortUrlId(shortUrlId)
                        .totalClicks(0L)
                        .build());

        return UrlAnalyticsResponse.builder()
                .shortUrlId(shortUrlId)
                .shortCode(shortCode)
                .totalClicks(analytics.getTotalClicks())
                .lastAccessedAt(analytics.getLastAccessedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<TopUrlResponse> getTopUrlsForCurrentUser(int limit) {
        UUID ownerId = getCurrentPrincipal().getId();
        return urlAnalyticsRepository.findTopByOwnerId(ownerId, limit).stream()
                .map(this::toTopUrlResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopUrlResponse> getSystemTopUrls(int limit) {
        return urlAnalyticsRepository.findTopSystemWide(limit).stream()
                .map(this::toTopUrlResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClickEventResponse> getRecentClicksForUrl(UUID shortUrlId, int limit) {
        assertUrlOwner(shortUrlId);
        return fetchRecentClicks(shortUrlId, limit);
    }

    @Transactional(readOnly = true)
    public List<ClickEventResponse> getRecentClicksForUrlAsAdmin(UUID shortUrlId, int limit) {
        urlAnalyticsRepository.findShortCodeByShortUrlId(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", shortUrlId.toString()));
        return fetchRecentClicks(shortUrlId, limit);
    }

    @Transactional(readOnly = true)
    public SystemStatsResponse getSystemStats() {
        return SystemStatsResponse.builder()
                .totalUsers(statsRepository.countActiveUsers())
                .totalUrls(statsRepository.countTotalUrls())
                .totalClicks(statsRepository.countTotalClicks())
                .activeUrls(statsRepository.countActiveUrls())
                .expiredUrls(statsRepository.countExpiredUrls())
                .deletedUrls(statsRepository.countDeletedUrls())
                .build();
    }

    private void assertUrlOwner(UUID shortUrlId) {
        UUID ownerId = urlAnalyticsRepository.findOwnerIdByShortUrlId(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", shortUrlId.toString()));

        UUID currentUserId = getCurrentPrincipal().getId();
        if (!ownerId.equals(currentUserId)) {
            throw new AccessDeniedException("You do not own this URL");
        }
    }

    private List<ClickEventResponse> fetchRecentClicks(UUID shortUrlId, int limit) {
        int cappedLimit = Math.min(Math.max(limit, 1), 100);
        return clickEventRepository
                .findByShortUrlIdOrderByClickedAtDesc(shortUrlId, PageRequest.of(0, cappedLimit))
                .stream()
                .map(this::toClickEventResponse)
                .toList();
    }

    private ClickEventResponse toClickEventResponse(ClickEvent event) {
        return ClickEventResponse.builder()
                .id(event.getId())
                .clickedAt(event.getClickedAt())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .referer(event.getReferer())
                .build();
    }

    private TopUrlResponse toTopUrlResponse(TopUrlProjection projection) {
        return TopUrlResponse.builder()
                .shortUrlId(projection.getShortUrlId())
                .shortCode(projection.getShortCode())
                .totalClicks(projection.getTotalClicks())
                .build();
    }

    private UserPrincipal getCurrentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}

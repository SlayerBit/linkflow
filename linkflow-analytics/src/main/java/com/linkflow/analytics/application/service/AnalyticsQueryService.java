package com.linkflow.analytics.application.service;

import com.linkflow.analytics.api.dto.ClickEventResponse;
import com.linkflow.analytics.api.dto.ClickTrendResponse;
import com.linkflow.analytics.api.dto.SystemStatsResponse;
import com.linkflow.analytics.api.dto.TopUrlResponse;
import com.linkflow.analytics.api.dto.UrlAnalyticsResponse;
import com.linkflow.analytics.domain.entity.ClickEvent;
import com.linkflow.analytics.domain.entity.UrlAnalytics;
import com.linkflow.analytics.domain.repository.ClickEventRepository;
import com.linkflow.analytics.domain.repository.StatsRepository;
import com.linkflow.analytics.domain.repository.UrlAnalyticsRepository;
import com.linkflow.analytics.domain.repository.projection.RecentClickProjection;
import com.linkflow.common.exception.ResourceNotFoundException;
import com.linkflow.common.port.UrlStatsPort;
import com.linkflow.common.port.UserLookupPort;
import com.linkflow.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsQueryService {

    private final UrlAnalyticsRepository urlAnalyticsRepository;
    private final ClickEventRepository clickEventRepository;
    private final StatsRepository statsRepository;
    private final UrlStatsPort urlStatsPort;
    private final UserLookupPort userLookupPort;

    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getUrlAnalytics(UUID shortUrlId) {
        UUID ownerId = urlStatsPort.findOwnerIdByShortUrlId(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", shortUrlId.toString()));

        UUID currentUserId = getCurrentPrincipal().getId();
        if (!ownerId.equals(currentUserId)) {
            throw new AccessDeniedException("You do not own this URL");
        }

        String shortCode = urlStatsPort.findShortCodeByShortUrlId(shortUrlId)
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
    public UrlAnalyticsResponse getUrlAnalyticsAsAdmin(UUID shortUrlId) {
        String shortCode = urlStatsPort.findShortCodeByShortUrlId(shortUrlId)
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
        return urlStatsPort.findTopByOwnerId(ownerId, limit).stream()
                .map(this::toTopUrlResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TopUrlResponse> getSystemTopUrls(int limit) {
        return urlStatsPort.findTopSystemWide(limit).stream()
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
        urlStatsPort.findShortCodeByShortUrlId(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", shortUrlId.toString()));
        return fetchRecentClicksWithRawIp(shortUrlId, limit);
    }

    @Transactional(readOnly = true)
    public SystemStatsResponse getSystemStats() {
        return SystemStatsResponse.builder()
                .totalUsers(userLookupPort.countActiveUsers())
                .totalUrls(urlStatsPort.countTotalUrls())
                .totalClicks(statsRepository.countTotalClicks())
                .activeUrls(urlStatsPort.countActiveUrls())
                .inactiveUrls(urlStatsPort.countInactiveUrls())
                .expiredUrls(urlStatsPort.countExpiredUrls())
                .deletedUrls(urlStatsPort.countDeletedUrls())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ClickTrendResponse> getClickTrendForUrl(UUID shortUrlId, int days) {
        assertUrlOwner(shortUrlId);
        Instant startDate = calculateStartDate(days);
        return clickEventRepository.findClickTrendByUrl(shortUrlId, startDate).stream()
                .map(projection -> ClickTrendResponse.builder()
                        .date(projection.getClickDate().toString())
                        .clicks(projection.getClickCount())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClickTrendResponse> getClickTrendForUrlAsAdmin(UUID shortUrlId, int days) {
        urlStatsPort.findShortCodeByShortUrlId(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", shortUrlId.toString()));
        Instant startDate = calculateStartDate(days);
        return clickEventRepository.findClickTrendByUrl(shortUrlId, startDate).stream()
                .map(projection -> ClickTrendResponse.builder()
                        .date(projection.getClickDate().toString())
                        .clicks(projection.getClickCount())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClickTrendResponse> getSystemClickTrend(int days) {
        Instant startDate = calculateStartDate(days);
        return clickEventRepository.findSystemClickTrend(startDate).stream()
                .map(projection -> ClickTrendResponse.builder()
                        .date(projection.getClickDate().toString())
                        .clicks(projection.getClickCount())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClickEventResponse> getRecentClicksForUser(int limit) {
        UUID ownerId = getCurrentPrincipal().getId();
        int cappedLimit = Math.min(Math.max(limit, 1), 100);
        return clickEventRepository.findRecentClicksProjectionByOwnerId(ownerId, cappedLimit).stream()
                .map(p -> toClickEventResponseWithMaskedIp(p))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ClickEventResponse> getSystemRecentClicks(int limit) {
        int cappedLimit = Math.min(Math.max(limit, 1), 100);
        return clickEventRepository.findRecentClicksProjectionSystemWide(cappedLimit).stream()
                .map(p -> toClickEventResponseRawIp(p))
                .toList();
    }

    private Instant calculateStartDate(int days) {
        int validatedDays = (days == 7 || days == 30 || days == 90) ? days : 30;
        return Instant.now().minus(java.time.Duration.ofDays(validatedDays));
    }

    private ClickEventResponse toClickEventResponseWithMaskedIp(RecentClickProjection p) {
        return ClickEventResponse.builder()
                .id(p.getId())
                .shortUrlId(p.getShortUrlId())
                .shortCode(p.getShortCode())
                .clickedAt(p.getClickedAt())
                .ipAddress(maskIpAddress(p.getIpAddress()))
                .userAgent(p.getUserAgent())
                .referer(p.getReferer())
                .build();
    }

    private ClickEventResponse toClickEventResponseRawIp(RecentClickProjection p) {
        return ClickEventResponse.builder()
                .id(p.getId())
                .shortUrlId(p.getShortUrlId())
                .shortCode(p.getShortCode())
                .clickedAt(p.getClickedAt())
                .ipAddress(p.getIpAddress())
                .userAgent(p.getUserAgent())
                .referer(p.getReferer())
                .build();
    }

    private String maskIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                return parts[0] + "." + parts[1] + ".x.x";
            }
            return "x.x.x.x";
        } else if (ip.contains(":")) {
            String[] parts = ip.split(":");
            if (parts.length >= 2) {
                return parts[0] + ":" + parts[1] + ":xxxx:xxxx:xxxx:xxxx:xxxx:xxxx";
            }
            return "xxxx:xxxx::";
        }
        return "xxx.xxx.xxx.xxx";
    }

    private void assertUrlOwner(UUID shortUrlId) {
        UUID ownerId = urlStatsPort.findOwnerIdByShortUrlId(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL", shortUrlId.toString()));

        UUID currentUserId = getCurrentPrincipal().getId();
        if (!ownerId.equals(currentUserId)) {
            throw new AccessDeniedException("You do not own this URL");
        }
    }

    private List<ClickEventResponse> fetchRecentClicksWithRawIp(UUID shortUrlId, int limit) {
        int cappedLimit = Math.min(Math.max(limit, 1), 100);
        return clickEventRepository
                .findByShortUrlIdOrderByClickedAtDesc(shortUrlId, PageRequest.of(0, cappedLimit))
                .stream()
                .map(this::toClickEventResponse)
                .toList();
    }

    private List<ClickEventResponse> fetchRecentClicks(UUID shortUrlId, int limit) {
        int cappedLimit = Math.min(Math.max(limit, 1), 100);
        return clickEventRepository
                .findByShortUrlIdOrderByClickedAtDesc(shortUrlId, PageRequest.of(0, cappedLimit))
                .stream()
                .map(this::toClickEventResponseWithMaskedIpFromEntity)
                .toList();
    }

    private ClickEventResponse toClickEventResponseWithMaskedIpFromEntity(ClickEvent event) {
        return ClickEventResponse.builder()
                .id(event.getId())
                .shortUrlId(event.getShortUrlId())
                .clickedAt(event.getClickedAt())
                .ipAddress(maskIpAddress(event.getIpAddress()))
                .userAgent(event.getUserAgent())
                .referer(event.getReferer())
                .build();
    }

    private ClickEventResponse toClickEventResponse(ClickEvent event) {
        return ClickEventResponse.builder()
                .id(event.getId())
                .shortUrlId(event.getShortUrlId())
                .clickedAt(event.getClickedAt())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .referer(event.getReferer())
                .build();
    }

    private TopUrlResponse toTopUrlResponse(UrlStatsPort.TopUrlData data) {
        return TopUrlResponse.builder()
                .shortUrlId(data.shortUrlId())
                .shortCode(data.shortCode())
                .totalClicks(data.totalClicks())
                .build();
    }

    private UserPrincipal getCurrentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}

package com.linkflow.analytics.application.service;

import com.linkflow.analytics.domain.entity.ClickEvent;
import com.linkflow.analytics.domain.entity.UrlAnalytics;
import com.linkflow.analytics.domain.repository.ClickEventRepository;
import com.linkflow.analytics.domain.repository.UrlAnalyticsRepository;
import com.linkflow.common.port.ClickTrackingPort.ClickTrackingCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Synchronous click persistence, used as the fallback path when Redis buffering is unavailable.
 * <p>
 * A separate bean from {@link ClickTrackingService} so the transaction is applied by the Spring
 * proxy. Exceptions deliberately propagate: swallowing them here would mark the transaction
 * rollback-only while reporting success to the caller. The caller decides that a failed analytics
 * write must not affect the redirect.
 */
@Service
@RequiredArgsConstructor
public class ClickPersistenceService {

    private final ClickEventRepository clickEventRepository;
    private final UrlAnalyticsRepository urlAnalyticsRepository;

    @Transactional
    public void recordClick(ClickTrackingCommand command) {
        Instant now = Instant.now();

        clickEventRepository.save(ClickEvent.builder()
                .shortUrlId(command.shortUrlId())
                .ipAddress(command.ipAddress())
                .userAgent(command.userAgent())
                .referer(command.referer())
                .clickedAt(now)
                .build());

        UrlAnalytics analytics = urlAnalyticsRepository.findByShortUrlId(command.shortUrlId())
                .orElseGet(() -> UrlAnalytics.builder()
                        .shortUrlId(command.shortUrlId())
                        .totalClicks(0L)
                        .build());

        analytics.setTotalClicks(analytics.getTotalClicks() + 1);
        analytics.setLastAccessedAt(now);
        urlAnalyticsRepository.save(analytics);
    }
}

package com.linkflow.analytics.application.service;

import com.linkflow.analytics.domain.entity.ClickEvent;
import com.linkflow.analytics.domain.entity.UrlAnalytics;
import com.linkflow.analytics.domain.repository.ClickEventRepository;
import com.linkflow.analytics.domain.repository.UrlAnalyticsRepository;
import com.linkflow.common.port.ClickTrackingPort.ClickTrackingCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickTrackingService {

    private final ClickEventRepository clickEventRepository;
    private final UrlAnalyticsRepository urlAnalyticsRepository;

    @Async("clickTrackingExecutor")
    @Transactional
    public void trackClick(ClickTrackingCommand command) {
        try {
            ClickEvent event = ClickEvent.builder()
                    .shortUrlId(command.shortUrlId())
                    .ipAddress(command.ipAddress())
                    .userAgent(command.userAgent())
                    .referer(command.referer())
                    .clickedAt(Instant.now())
                    .build();
            clickEventRepository.save(event);

            UrlAnalytics analytics = urlAnalyticsRepository.findByShortUrlId(command.shortUrlId())
                    .orElseGet(() -> UrlAnalytics.builder()
                            .shortUrlId(command.shortUrlId())
                            .totalClicks(0L)
                            .build());

            analytics.setTotalClicks(analytics.getTotalClicks() + 1);
            analytics.setLastAccessedAt(Instant.now());
            urlAnalyticsRepository.save(analytics);
        } catch (Exception ex) {
            log.warn("Failed to track click for shortUrlId={}: {}", command.shortUrlId(), ex.getMessage());
        }
    }
}

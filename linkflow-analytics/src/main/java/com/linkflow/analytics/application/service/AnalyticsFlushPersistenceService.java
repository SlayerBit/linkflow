package com.linkflow.analytics.application.service;

import com.linkflow.analytics.domain.entity.ClickEvent;
import com.linkflow.analytics.domain.entity.UrlAnalytics;
import com.linkflow.analytics.domain.repository.ClickEventRepository;
import com.linkflow.analytics.domain.repository.UrlAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalyticsFlushPersistenceService {

    private final ClickEventRepository clickEventRepository;
    private final UrlAnalyticsRepository urlAnalyticsRepository;

    @Transactional
    public boolean persistClickEventIfAbsent(ClickEvent event) {
        if (clickEventRepository.existsByStreamRecordId(event.getStreamRecordId())) {
            return false;
        }
        clickEventRepository.save(event);
        return true;
    }

    @Transactional
    public void applyCounterFlush(UUID shortUrlId, long count) {
        UrlAnalytics analytics = urlAnalyticsRepository.findByShortUrlId(shortUrlId)
                .orElseGet(() -> UrlAnalytics.builder()
                        .shortUrlId(shortUrlId)
                        .totalClicks(0L)
                        .build());
        analytics.setTotalClicks(analytics.getTotalClicks() + count);
        analytics.setLastAccessedAt(Instant.now());
        urlAnalyticsRepository.save(analytics);
    }

    @Transactional
    public int purgeClickEventsOlderThan(Instant cutoff) {
        return clickEventRepository.deleteOlderThan(cutoff);
    }
}

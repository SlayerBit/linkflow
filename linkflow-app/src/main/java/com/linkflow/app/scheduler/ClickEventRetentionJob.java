package com.linkflow.app.scheduler;

import com.linkflow.analytics.application.service.AnalyticsFlushPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClickEventRetentionJob {

    private final AnalyticsFlushPersistenceService analyticsFlushPersistenceService;

    @Value("${linkflow.analytics.click-events-retention-days:365}")
    private int retentionDays;

    @Scheduled(cron = "${linkflow.analytics.click-events-retention-cron:0 30 4 * * *}")
    @SchedulerLock(name = "ClickEventRetentionJob_purge", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void purgeOldEvents() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = analyticsFlushPersistenceService.purgeClickEventsOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Purged {} click events older than {} days", deleted, retentionDays);
        }
    }
}

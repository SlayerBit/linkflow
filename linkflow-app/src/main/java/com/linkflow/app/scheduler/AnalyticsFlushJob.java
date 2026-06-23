package com.linkflow.app.scheduler;

import com.linkflow.analytics.application.service.AnalyticsFlushService;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalyticsFlushJob {

    private final AnalyticsFlushService analyticsFlushService;

    @Scheduled(fixedDelayString = "${linkflow.analytics.flush-interval-ms:30000}")
    @SchedulerLock(name = "AnalyticsFlushJob_flush", lockAtMostFor = "5m", lockAtLeastFor = "10s")
    public void flush() {
        analyticsFlushService.flush();
    }
}

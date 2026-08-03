package com.linkflow.app.scheduler;

import com.linkflow.auth.application.service.SingleUseTokenCleanupService;
import com.linkflow.user.application.service.EmailChangeCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Reaps spent and expired verification, password-reset, and email-change tokens.
 * <p>
 * ShedLock keeps this to one instance per run so multiple replicas do not issue competing
 * DELETEs against the same rows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SingleUseTokenCleanupJob {

    private final SingleUseTokenCleanupService singleUseTokenCleanupService;
    private final EmailChangeCleanupService emailChangeCleanupService;

    @Value("${linkflow.auth.single-use-token-retention-days:7}")
    private int retentionDays;

    @Scheduled(cron = "${linkflow.auth.single-use-token-cleanup-cron:0 45 3 * * *}")
    @SchedulerLock(name = "SingleUseTokenCleanupJob_cleanup", lockAtMostFor = "10m", lockAtLeastFor = "30s")
    public void cleanup() {
        Duration retention = Duration.ofDays(retentionDays);

        int authTokens = singleUseTokenCleanupService.cleanup(retention);
        int emailChangeRequests = emailChangeCleanupService.cleanup(retention);

        int total = authTokens + emailChangeRequests;
        if (total > 0) {
            log.info("Single-use token cleanup removed {} rows", total);
        }
    }
}

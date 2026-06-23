package com.linkflow.app.scheduler;

import com.linkflow.auth.application.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    @Value("${linkflow.auth.refresh-token-revoked-retention-days:7}")
    private int revokedRetentionDays;

    @Scheduled(cron = "${linkflow.auth.refresh-token-cleanup-cron:0 15 3 * * *}")
    @SchedulerLock(name = "RefreshTokenCleanupJob_cleanup", lockAtMostFor = "10m", lockAtLeastFor = "30s")
    public void cleanup() {
        int deleted = refreshTokenService.cleanupExpiredAndRevoked(Duration.ofDays(revokedRetentionDays));
        if (deleted > 0) {
            log.info("Refresh token cleanup removed {} rows", deleted);
        }
    }
}

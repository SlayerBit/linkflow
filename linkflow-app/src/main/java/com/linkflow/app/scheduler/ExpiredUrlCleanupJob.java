package com.linkflow.app.scheduler;

import com.linkflow.url.application.service.UrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredUrlCleanupJob {

    private final UrlService urlService;

    @Scheduled(cron = "0 0 * * * *")
    public void cleanup() {
        int deactivated = urlService.deactivateExpiredUrls();
        int idempotencyRemoved = urlService.cleanupExpiredIdempotencyRecords();
        if (deactivated > 0 || idempotencyRemoved > 0) {
            log.info("Cleanup job completed: deactivatedUrls={}, removedIdempotencyRecords={}",
                    deactivated, idempotencyRemoved);
        }
    }
}

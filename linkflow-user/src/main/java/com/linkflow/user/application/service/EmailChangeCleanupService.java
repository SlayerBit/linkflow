package com.linkflow.user.application.service;

import com.linkflow.user.domain.repository.EmailChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Purges email change requests that can no longer be confirmed, bounding growth of the table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailChangeCleanupService {

    private final EmailChangeRequestRepository emailChangeRequestRepository;

    @Transactional
    public int cleanup(Duration retention) {
        Instant now = Instant.now();
        int deleted = emailChangeRequestRepository.deleteUnusableCreatedBefore(now, now.minus(retention));
        if (deleted > 0) {
            log.debug("Purged {} email change requests older than {}", deleted, retention);
        }
        return deleted;
    }
}

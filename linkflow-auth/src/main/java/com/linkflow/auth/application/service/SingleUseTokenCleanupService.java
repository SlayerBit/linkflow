package com.linkflow.auth.application.service;

import com.linkflow.auth.domain.repository.EmailVerificationTokenRepository;
import com.linkflow.auth.domain.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Purges verification and password-reset tokens that can no longer be redeemed.
 * <p>
 * Every registration and recovery attempt writes a row here, so without a reaper these tables
 * grow monotonically for the life of the deployment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SingleUseTokenCleanupService {

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * @param retention how long an unusable token is kept before deletion
     * @return number of rows removed across both tables
     */
    @Transactional
    public int cleanup(Duration retention) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(retention);

        int verificationTokens = emailVerificationTokenRepository.deleteUnusableCreatedBefore(now, cutoff);
        int resetTokens = passwordResetTokenRepository.deleteUnusableCreatedBefore(now, cutoff);

        int total = verificationTokens + resetTokens;
        if (total > 0) {
            log.debug("Purged {} verification and {} reset tokens older than {}",
                    verificationTokens, resetTokens, retention);
        }
        return total;
    }
}

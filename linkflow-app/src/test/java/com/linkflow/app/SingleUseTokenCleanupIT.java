package com.linkflow.app;

import com.linkflow.app.scheduler.SingleUseTokenCleanupJob;
import com.linkflow.app.support.AbstractIntegrationTest;
import com.linkflow.app.support.TestMailbox;
import com.linkflow.auth.application.service.SingleUseTokenCleanupService;
import com.linkflow.auth.domain.repository.EmailVerificationTokenRepository;
import com.linkflow.auth.domain.repository.PasswordResetTokenRepository;
import com.linkflow.common.security.SecureTokenGenerator;
import com.linkflow.user.application.service.EmailChangeCleanupService;
import com.linkflow.user.domain.repository.EmailChangeRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the reaper that keeps the single-use token tables from growing for the life of the
 * deployment.
 * <p>
 * Two properties matter, and they pull in opposite directions. A token that can still be redeemed
 * must never be deleted — losing one strands a user mid-recovery with a link that silently stops
 * working. A token that cannot be redeemed must eventually go, or every registration and every
 * forgotten password leaves a row behind permanently.
 * <p>
 * The retention window is driven by passing an explicit {@link Duration} rather than by
 * back-dating rows, so the tests read the way the boundary is actually defined: zero retention
 * means "purge everything already unusable", and the production default of seven days means
 * "nothing written this week".
 */
class SingleUseTokenCleanupIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "StrongP@ss1";
    private static final Duration PRODUCTION_RETENTION = Duration.ofDays(7);

    @Autowired
    private SingleUseTokenCleanupService cleanupService;

    @Autowired
    private EmailChangeCleanupService emailChangeCleanupService;

    @Autowired
    private SingleUseTokenCleanupJob cleanupJob;

    @Autowired
    private EmailVerificationTokenRepository verificationTokens;

    @Autowired
    private PasswordResetTokenRepository resetTokens;

    @Autowired
    private EmailChangeRequestRepository emailChangeRequests;

    @Autowired
    private SecureTokenGenerator tokenGenerator;

    @Test
    void aRedeemedVerificationTokenIsKeptForTheRetentionWindowThenPurged() throws Exception {
        String email = uniqueEmail("purge-verify");
        register(email, PASSWORD, "Purge");
        String rawToken = TestMailbox.awaitToken(email, "/verify-email");
        String hash = tokenGenerator.hash(rawToken);

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(rawToken)))
                .andExpect(status().isOk());

        // Spent, but recent. Kept so that "this link was already used" stays distinguishable from
        // "this link never existed" while a user is still likely to ask about it.
        cleanupService.cleanup(PRODUCTION_RETENTION);
        assertTrue(verificationTokens.findByTokenHash(hash).isPresent(),
                "a token spent moments ago must survive the retention window");

        cleanupService.cleanup(Duration.ZERO);
        assertFalse(verificationTokens.findByTokenHash(hash).isPresent(),
                "a spent token past its retention window must be removed");
    }

    /**
     * The failure this guards against is quiet and bad: a user who registered, went to lunch, and
     * came back to a link that had been deleted from under them.
     */
    @Test
    void aLiveVerificationTokenIsNeverPurged() throws Exception {
        String email = uniqueEmail("keep-verify");
        register(email, PASSWORD, "Keep");
        String rawToken = TestMailbox.awaitToken(email, "/verify-email");

        cleanupService.cleanup(Duration.ZERO);

        assertTrue(verificationTokens.findByTokenHash(tokenGenerator.hash(rawToken)).isPresent(),
                "an unused, unexpired token is still redeemable and must be kept");

        // Proving the row survived is not the same as proving the link still works.
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(rawToken)))
                .andExpect(status().isOk());
    }

    @Test
    void aSupersededPasswordResetTokenIsPurgedOnceUnusable() throws Exception {
        String email = uniqueEmail("purge-reset");
        registerUser(email, PASSWORD, "PurgeReset");

        requestPasswordReset(email);
        String firstRaw = TestMailbox.awaitToken(email, "/reset-password");

        // Requesting again retires the first token without redeeming it, which is the most common
        // way one of these rows becomes garbage.
        TestMailbox.clear();
        requestPasswordReset(email);
        String secondRaw = TestMailbox.awaitToken(email, "/reset-password");

        cleanupService.cleanup(Duration.ZERO);

        assertFalse(resetTokens.findByTokenHash(tokenGenerator.hash(firstRaw)).isPresent(),
                "the superseded reset token is unusable and should be gone");
        assertTrue(resetTokens.findByTokenHash(tokenGenerator.hash(secondRaw)).isPresent(),
                "the current reset token is still redeemable and must be kept");
    }

    @Test
    void aRedeemedEmailChangeRequestIsPurgedOnceUnusable() throws Exception {
        String original = uniqueEmail("purge-change-from");
        String replacement = uniqueEmail("purge-change-to");
        registerUser(original, PASSWORD, "PurgeChange");
        String accessToken = login(original, PASSWORD).accessToken();

        mockMvc.perform(post("/api/v1/users/me/email-change-request")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "%s", "newEmail": "%s"}
                                """.formatted(PASSWORD, replacement)))
                .andExpect(status().isOk());

        String rawToken = TestMailbox.awaitToken(replacement, "/verify-email-change");

        emailChangeCleanupService.cleanup(Duration.ZERO);
        assertTrue(emailChangeRequests.findByTokenHash(tokenGenerator.hash(rawToken)).isPresent(),
                "an outstanding confirmation link must not be swept away before it is used");

        mockMvc.perform(post("/api/v1/users/verify-email-change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\"}".formatted(rawToken)))
                .andExpect(status().isOk());

        emailChangeCleanupService.cleanup(Duration.ZERO);
        assertFalse(emailChangeRequests.findByTokenHash(tokenGenerator.hash(rawToken)).isPresent(),
                "a redeemed confirmation link should be reaped");
    }

    /**
     * The scheduled entry point, run directly rather than waiting on its 03:45 cron. Confirms that
     * both cleanup services are actually reachable from the job and that the default retention is
     * what the job applies — a job wired to only one of them, or to a zero retention, would pass
     * every test above and still be wrong.
     */
    @Test
    void theScheduledJobAppliesTheDefaultRetentionAcrossBothStores() throws Exception {
        String email = uniqueEmail("job");
        register(email, PASSWORD, "Job");
        String rawToken = TestMailbox.awaitToken(email, "/verify-email");

        cleanupJob.cleanup();

        assertTrue(verificationTokens.findByTokenHash(tokenGenerator.hash(rawToken)).isPresent(),
                "the seven-day default retention must leave a token issued seconds ago alone");
    }

    private void requestPasswordReset(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\"}".formatted(email)))
                .andExpect(status().isOk());
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@example.com";
    }
}

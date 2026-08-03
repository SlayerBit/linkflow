package com.linkflow.common.event;

import java.time.Duration;

/**
 * Signals that a business operation has created a single-use token needing delivery by email.
 * <p>
 * Publishers raise these events <em>inside</em> the transaction that persists the token, and the
 * listener in linkflow-notification handles them after commit. That ordering matters: dispatching
 * mail directly would race the commit, and a recipient can be fast enough to open a link before
 * the token row is visible — or receive a link for a transaction that then rolled back.
 * <p>
 * Sealed so the dispatcher's switch is exhaustive and a new email type cannot be added without
 * the compiler pointing at the code that must handle it.
 */
public sealed interface EmailRequestedEvent {

    String recipient();

    String firstName();

    String rawToken();

    Duration validFor();

    record EmailVerificationRequested(
            String recipient,
            String firstName,
            String rawToken,
            Duration validFor
    ) implements EmailRequestedEvent {}

    record PasswordResetRequested(
            String recipient,
            String firstName,
            String rawToken,
            Duration validFor
    ) implements EmailRequestedEvent {}

    /**
     * @param recipient the new address being claimed, which is where the confirmation link is sent
     */
    record EmailChangeRequested(
            String recipient,
            String firstName,
            String rawToken,
            Duration validFor
    ) implements EmailRequestedEvent {}
}

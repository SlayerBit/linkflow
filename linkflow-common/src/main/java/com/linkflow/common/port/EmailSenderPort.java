package com.linkflow.common.port;

import java.time.Duration;

/**
 * Port interface for transactional email delivery. Implemented by linkflow-notification.
 * Consumed by linkflow-auth and linkflow-user so neither module depends on a mail transport.
 * <p>
 * Implementations are expected to deliver asynchronously and must never propagate delivery
 * failures to the caller: a mail outage must not fail the business operation that triggered it.
 */
public interface EmailSenderPort {

    void sendEmailVerification(VerificationEmail email);

    void sendPasswordReset(PasswordResetEmail email);

    void sendEmailChangeVerification(EmailChangeEmail email);

    /**
     * @param recipient  destination address
     * @param firstName  used for the salutation; may be blank
     * @param rawToken   single-use token; the implementation builds the verification link
     * @param validFor   token lifetime, rendered in the message body
     */
    record VerificationEmail(String recipient, String firstName, String rawToken, Duration validFor) {}

    record PasswordResetEmail(String recipient, String firstName, String rawToken, Duration validFor) {}

    /**
     * @param recipient the <em>new</em> address being claimed, which is where the link is sent
     */
    record EmailChangeEmail(String recipient, String firstName, String rawToken, Duration validFor) {}
}

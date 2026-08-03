package com.linkflow.notification.listener;

import com.linkflow.common.event.EmailDeliveryEvent;
import com.linkflow.common.event.EmailRequestedEvent;
import com.linkflow.common.event.EmailRequestedEvent.EmailChangeRequested;
import com.linkflow.common.event.EmailRequestedEvent.EmailVerificationRequested;
import com.linkflow.common.event.EmailRequestedEvent.PasswordResetRequested;
import com.linkflow.common.port.EmailSenderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.RejectedExecutionException;

/**
 * Turns committed token-issuing operations into outbound email.
 * <p>
 * Bound to {@link TransactionPhase#AFTER_COMMIT} so a rolled-back registration or reset request
 * never produces a message referencing a token that does not exist. Delivery itself is async
 * inside the adapter, so this listener returns immediately and never extends the request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionalEmailDispatcher {

    private final EmailSenderPort emailSender;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailRequested(EmailRequestedEvent event) {
        EmailDeliveryEvent.Type type = typeOf(event);
        try {
            dispatch(event);
        } catch (RejectedExecutionException ex) {
            // The mail executor's queue is full, so this message will never be sent. It has to be
            // reported here rather than left to Spring: an exception thrown from an after-commit
            // listener is logged without the context needed to act on it, and the transaction has
            // already committed, so a token now exists that nobody will ever receive.
            //
            // Reported as a delivery failure as well as a log line, so the same counter that tracks
            // SMTP failures also catches messages that never reached SMTP at all.
            log.error("Mail executor rejected a {} email for {}; it will not be delivered. "
                            + "The recipient must use the resend flow. Cause: {}",
                    type, event.recipient(), ex.getMessage());
            eventPublisher.publishEvent(new EmailDeliveryEvent(type, EmailDeliveryEvent.Outcome.FAILED));
        }
    }

    private void dispatch(EmailRequestedEvent event) {
        switch (event) {
            case EmailVerificationRequested e -> emailSender.sendEmailVerification(
                    new EmailSenderPort.VerificationEmail(
                            e.recipient(), e.firstName(), e.rawToken(), e.validFor()));

            case PasswordResetRequested e -> emailSender.sendPasswordReset(
                    new EmailSenderPort.PasswordResetEmail(
                            e.recipient(), e.firstName(), e.rawToken(), e.validFor()));

            case EmailChangeRequested e -> emailSender.sendEmailChangeVerification(
                    new EmailSenderPort.EmailChangeEmail(
                            e.recipient(), e.firstName(), e.rawToken(), e.validFor()));
        }
    }

    private EmailDeliveryEvent.Type typeOf(EmailRequestedEvent event) {
        return switch (event) {
            case EmailVerificationRequested ignored -> EmailDeliveryEvent.Type.EMAIL_VERIFICATION;
            case PasswordResetRequested ignored -> EmailDeliveryEvent.Type.PASSWORD_RESET;
            case EmailChangeRequested ignored -> EmailDeliveryEvent.Type.EMAIL_CHANGE;
        };
    }
}

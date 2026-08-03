package com.linkflow.notification.adapter;

import com.linkflow.common.event.EmailDeliveryEvent;
import com.linkflow.common.port.EmailSenderPort;
import com.linkflow.notification.config.MailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * SMTP implementation of {@link EmailSenderPort}.
 * <p>
 * Every send is asynchronous and failure-tolerant: a mail outage degrades account recovery but
 * must never fail the registration or reset request that triggered it. Callers dispatch after
 * transaction commit, so a message is only ever sent for a change that actually persisted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSenderAdapter implements EmailSenderPort {

    private static final String VERIFY_PATH = "/verify-email?token=";
    private static final String RESET_PATH = "/reset-password?token=";
    private static final String EMAIL_CHANGE_PATH = "/verify-email-change?token=";

    private final JavaMailSender mailSender;
    private final TemplateEngine emailTemplateEngine;
    private final MailProperties mailProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Async("emailExecutor")
    public void sendEmailVerification(VerificationEmail email) {
        deliver(new Message(
                EmailDeliveryEvent.Type.EMAIL_VERIFICATION,
                "email/verification",
                email.recipient(),
                "Confirm your LinkFlow email address",
                email.firstName(),
                link(VERIFY_PATH, email.rawToken()),
                email.validFor()
        ));
    }

    @Override
    @Async("emailExecutor")
    public void sendPasswordReset(PasswordResetEmail email) {
        deliver(new Message(
                EmailDeliveryEvent.Type.PASSWORD_RESET,
                "email/password-reset",
                email.recipient(),
                "Reset your LinkFlow password",
                email.firstName(),
                link(RESET_PATH, email.rawToken()),
                email.validFor()
        ));
    }

    @Override
    @Async("emailExecutor")
    public void sendEmailChangeVerification(EmailChangeEmail email) {
        deliver(new Message(
                EmailDeliveryEvent.Type.EMAIL_CHANGE,
                "email/email-change",
                email.recipient(),
                "Confirm your new LinkFlow email address",
                email.firstName(),
                link(EMAIL_CHANGE_PATH, email.rawToken()),
                email.validFor()
        ));
    }

    private void deliver(Message message) {
        if (!mailProperties.isEnabled()) {
            // Not a production path: MailConfigValidator rejects disabled mail in the prod profile.
            // Logging the link keeps local development workable without an SMTP catcher running.
            log.info("Mail delivery disabled — {} link for {}: {}",
                    message.type(), message.recipient(), message.actionUrl());
            eventPublisher.publishEvent(
                    new EmailDeliveryEvent(message.type(), EmailDeliveryEvent.Outcome.SKIPPED));
            return;
        }

        int maxAttempts = Math.max(1, mailProperties.getMaxAttempts());
        long backoffMs = mailProperties.getRetryBackoffMs();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                mailSender.send(build(message));
                log.info("Sent {} email to {} (attempt {}/{})",
                        message.type(), message.recipient(), attempt, maxAttempts);
                eventPublisher.publishEvent(
                        new EmailDeliveryEvent(message.type(), EmailDeliveryEvent.Outcome.SENT));
                return;
            } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
                boolean lastAttempt = attempt == maxAttempts;
                if (lastAttempt) {
                    // Recipient address is logged for operability; the token is never logged.
                    log.error("Giving up on {} email to {} after {} attempts: {}",
                            message.type(), message.recipient(), maxAttempts, ex.getMessage());
                    eventPublisher.publishEvent(
                            new EmailDeliveryEvent(message.type(), EmailDeliveryEvent.Outcome.FAILED));
                    return;
                }
                log.warn("Attempt {}/{} to send {} email to {} failed, retrying in {}ms: {}",
                        attempt, maxAttempts, message.type(), message.recipient(), backoffMs, ex.getMessage());
                if (!sleep(backoffMs)) {
                    eventPublisher.publishEvent(
                            new EmailDeliveryEvent(message.type(), EmailDeliveryEvent.Outcome.FAILED));
                    return;
                }
                backoffMs *= 2;
            }
        }
    }

    private MimeMessage build(Message message) throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

        helper.setFrom(mailProperties.getFromAddress(), mailProperties.getFromName());
        helper.setTo(message.recipient());
        helper.setSubject(message.subject());
        // Plain-text alternative first, then HTML: required ordering for setText(text, html).
        helper.setText(renderPlainText(message), renderHtml(message));

        return mimeMessage;
    }

    private String renderHtml(Message message) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("greetingName", greetingName(message.firstName()));
        context.setVariable("actionUrl", message.actionUrl());
        context.setVariable("validForText", humanizeDuration(message.validFor()));
        context.setVariable("supportFrom", mailProperties.getFromAddress());
        return emailTemplateEngine.process(message.template(), context);
    }

    /**
     * Plain-text alternative for clients that do not render HTML, and to reduce the spam score
     * that HTML-only messages attract.
     */
    private String renderPlainText(Message message) {
        return """
                Hi %s,

                %s

                %s

                This link expires in %s and can only be used once.
                If you did not request this, you can safely ignore this email.

                — LinkFlow
                """.formatted(
                greetingName(message.firstName()),
                plainTextIntro(message.type()),
                message.actionUrl(),
                humanizeDuration(message.validFor()));
    }

    private String plainTextIntro(EmailDeliveryEvent.Type type) {
        return switch (type) {
            case EMAIL_VERIFICATION -> "Confirm your email address to activate your LinkFlow account:";
            case PASSWORD_RESET -> "Use the link below to choose a new password:";
            case EMAIL_CHANGE -> "Confirm this address to finish changing your LinkFlow email:";
        };
    }

    private String greetingName(String firstName) {
        return (firstName == null || firstName.isBlank()) ? "there" : firstName;
    }

    private String link(String path, String rawToken) {
        String base = mailProperties.getBaseUrl();
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        // Tokens are generated with a URL-safe alphabet, so no percent-encoding is required.
        return trimmed + path + rawToken;
    }

    /**
     * Renders an expiry window as hours or minutes. Deliberately does not collapse into days —
     * "24 hours" states the deadline more precisely than "1 day" for someone deciding whether a
     * link they received yesterday still works.
     */
    private String humanizeDuration(Duration duration) {
        long hours = duration.toHours();
        if (hours >= 1) {
            return hours == 1 ? "1 hour" : hours + " hours";
        }
        long minutes = Math.max(1, duration.toMinutes());
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }

    private boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Email retry backoff interrupted; abandoning delivery");
            return false;
        }
    }

    private record Message(
            EmailDeliveryEvent.Type type,
            String template,
            String recipient,
            String subject,
            String firstName,
            String actionUrl,
            Duration validFor
    ) {}
}

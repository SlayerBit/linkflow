package com.linkflow.notification.adapter;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.linkflow.common.event.EmailDeliveryEvent;
import com.linkflow.common.port.EmailSenderPort;
import com.linkflow.notification.config.EmailTemplateConfig;
import com.linkflow.notification.config.MailProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Exercises delivery against a real in-JVM SMTP server rather than a mocked JavaMailSender, so
 * these tests cover template rendering, MIME assembly, and the SMTP conversation end to end.
 */
class EmailSenderAdapterTest {

    @RegisterExtension
    static final GreenMailExtension GREEN_MAIL =
            new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(true);

    private static final Duration TTL = Duration.ofHours(24);

    private EmailSenderAdapter adapter;
    private MailProperties mailProperties;
    private List<EmailDeliveryEvent> publishedEvents;

    @BeforeEach
    void setUp() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("127.0.0.1");
        mailSender.setPort(GREEN_MAIL.getSmtp().getPort());

        mailProperties = new MailProperties();
        mailProperties.setFromAddress("no-reply@linkflow.test");
        mailProperties.setFromName("LinkFlow");
        mailProperties.setBaseUrl("https://links.example.com");
        mailProperties.setMaxAttempts(2);
        mailProperties.setRetryBackoffMs(1);

        publishedEvents = new ArrayList<>();
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        doAnswer(invocation -> {
            publishedEvents.add(invocation.getArgument(0));
            return null;
        }).when(publisher).publishEvent(any(EmailDeliveryEvent.class));

        adapter = new EmailSenderAdapter(
                mailSender,
                new EmailTemplateConfig().emailTemplateEngine(),
                mailProperties,
                publisher);
    }

    @Test
    void sendEmailVerification_deliversRenderedMessageWithWorkingLink() throws Exception {
        adapter.sendEmailVerification(new EmailSenderPort.VerificationEmail(
                "ada@example.com", "Ada", "tok-abc123", TTL));

        assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();
        MimeMessage received = GREEN_MAIL.getReceivedMessages()[0];

        assertThat(received.getSubject()).isEqualTo("Confirm your LinkFlow email address");
        assertThat(received.getAllRecipients()[0]).hasToString("ada@example.com");
        assertThat(received.getFrom()[0].toString()).contains("no-reply@linkflow.test");

        String body = bodyOf(received);
        assertThat(body)
                .contains("https://links.example.com/verify-email?token=tok-abc123")
                .contains("Ada")
                .contains("24 hours");
    }

    @Test
    void sendPasswordReset_usesResetPathAndRendersExpiryInMinutes() throws Exception {
        adapter.sendPasswordReset(new EmailSenderPort.PasswordResetEmail(
                "ada@example.com", "Ada", "reset-xyz", Duration.ofMinutes(15)));

        assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();
        MimeMessage received = GREEN_MAIL.getReceivedMessages()[0];

        assertThat(received.getSubject()).isEqualTo("Reset your LinkFlow password");
        assertThat(bodyOf(received))
                .contains("https://links.example.com/reset-password?token=reset-xyz")
                .contains("15 minutes");
    }

    @Test
    void sendEmailChangeVerification_addressesTheNewMailbox() throws Exception {
        adapter.sendEmailChangeVerification(new EmailSenderPort.EmailChangeEmail(
                "new-address@example.com", "Ada", "change-tok", TTL));

        assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();
        MimeMessage received = GREEN_MAIL.getReceivedMessages()[0];

        assertThat(received.getAllRecipients()[0]).hasToString("new-address@example.com");
        assertThat(bodyOf(received))
                .contains("https://links.example.com/verify-email-change?token=change-tok");
    }

    @Test
    void sendsBothPlainTextAndHtmlAlternatives() throws Exception {
        adapter.sendEmailVerification(new EmailSenderPort.VerificationEmail(
                "ada@example.com", "Ada", "tok", TTL));

        assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();
        String body = bodyOf(GREEN_MAIL.getReceivedMessages()[0]);

        assertThat(body).contains("text/plain").contains("text/html");
    }

    @Test
    void blankFirstNameFallsBackToNeutralSalutation() throws Exception {
        adapter.sendEmailVerification(new EmailSenderPort.VerificationEmail(
                "ada@example.com", "  ", "tok", TTL));

        assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();
        assertThat(bodyOf(GREEN_MAIL.getReceivedMessages()[0])).contains("Hi there");
    }

    @Test
    void trailingSlashOnBaseUrlDoesNotProduceADoubleSlash() throws Exception {
        mailProperties.setBaseUrl("https://links.example.com/");

        adapter.sendEmailVerification(new EmailSenderPort.VerificationEmail(
                "ada@example.com", "Ada", "tok", TTL));

        assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();
        assertThat(bodyOf(GREEN_MAIL.getReceivedMessages()[0]))
                .contains("https://links.example.com/verify-email?token=tok")
                .doesNotContain("com//verify-email");
    }

    @Test
    void publishesSentEventOnSuccess() {
        adapter.sendEmailVerification(new EmailSenderPort.VerificationEmail(
                "ada@example.com", "Ada", "tok", TTL));

        assertThat(GREEN_MAIL.waitForIncomingEmail(5_000, 1)).isTrue();
        assertThat(publishedEvents).containsExactly(new EmailDeliveryEvent(
                EmailDeliveryEvent.Type.EMAIL_VERIFICATION, EmailDeliveryEvent.Outcome.SENT));
    }

    @Test
    void whenDisabled_nothingIsSentAndOutcomeIsSkipped() {
        mailProperties.setEnabled(false);

        adapter.sendEmailVerification(new EmailSenderPort.VerificationEmail(
                "ada@example.com", "Ada", "tok", TTL));

        assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty();
        assertThat(publishedEvents).containsExactly(new EmailDeliveryEvent(
                EmailDeliveryEvent.Type.EMAIL_VERIFICATION, EmailDeliveryEvent.Outcome.SKIPPED));
    }

    @Test
    void unreachableSmtpIsSwallowedAndReportedAsFailed() {
        JavaMailSenderImpl broken = new JavaMailSenderImpl();
        broken.setHost("127.0.0.1");
        // Port with no listener: exercises the exhausted-retry path.
        broken.setPort(1);

        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        doAnswer(invocation -> {
            publishedEvents.add(invocation.getArgument(0));
            return null;
        }).when(publisher).publishEvent(any(EmailDeliveryEvent.class));

        EmailSenderAdapter failing = new EmailSenderAdapter(
                broken, new EmailTemplateConfig().emailTemplateEngine(), mailProperties, publisher);

        // A mail outage must not surface to the caller — registration still has to succeed.
        failing.sendEmailVerification(new EmailSenderPort.VerificationEmail(
                "ada@example.com", "Ada", "tok", TTL));

        assertThat(publishedEvents).containsExactly(new EmailDeliveryEvent(
                EmailDeliveryEvent.Type.EMAIL_VERIFICATION, EmailDeliveryEvent.Outcome.FAILED));
    }

    /**
     * Returns the full MIME source with quoted-printable encoding undone, so assertions can match
     * URLs and prose that the transfer encoding would otherwise split across lines or escape.
     * Keeping both parts in one string lets a single assertion cover the text and HTML alternatives.
     */
    private String bodyOf(MimeMessage message) throws Exception {
        var buffer = new ByteArrayOutputStream();
        message.writeTo(buffer);
        return buffer.toString(StandardCharsets.UTF_8)
                .replace("=\r\n", "")
                .replace("=\n", "")
                .replace("=3D", "=");
    }
}

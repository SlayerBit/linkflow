package com.linkflow.app.support;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-JVM SMTP server for integration tests, plus helpers to pull single-use tokens back out of
 * delivered messages.
 * <p>
 * Since raw tokens are never returned by the API and only their hashes are stored, reading the
 * email is the only way a test can complete a verification or reset flow — which is exactly the
 * path a real user takes.
 */
public final class TestMailbox {

    private static final GreenMail SMTP = new GreenMail(ServerSetupTest.SMTP);

    /** Tokens use the URL-safe Base64 alphabet, so no other characters can appear in the link. */
    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private static final long DELIVERY_TIMEOUT_MS = 10_000;

    private static final long NEGATIVE_ASSERTION_WINDOW_MS = 1_000;

    private TestMailbox() {
    }

    public static void start() {
        if (!SMTP.isRunning()) {
            SMTP.start();
        }
    }

    public static int port() {
        return ServerSetupTest.SMTP.getPort();
    }

    public static void clear() {
        if (!SMTP.isRunning()) {
            return;
        }
        try {
            SMTP.purgeEmailFromAllMailboxes();
        } catch (com.icegreen.greenmail.store.FolderException e) {
            throw new IllegalStateException("Could not reset the test mailbox between tests", e);
        }
    }

    /**
     * Waits for a message addressed to {@code recipient} whose link path matches {@code linkPath},
     * then returns the token from that link.
     * <p>
     * Delivery is asynchronous and fires after transaction commit, so tests must wait rather than
     * assume the message has already arrived.
     *
     * @param linkPath e.g. {@code "/verify-email"} or {@code "/reset-password"}
     * @throws AssertionError if no matching message arrives in time
     */
    public static String awaitToken(String recipient, String linkPath) {
        long deadline = System.currentTimeMillis() + DELIVERY_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            Optional<String> token = findToken(recipient, linkPath);
            if (token.isPresent()) {
                return token.get();
            }
            SMTP.waitForIncomingEmail(250, currentCount() + 1);
        }

        throw new AssertionError("No email containing a %s link arrived for %s within %dms. Received: %s"
                .formatted(linkPath, recipient, DELIVERY_TIMEOUT_MS, describeReceived()));
    }

    /**
     * Asserts that no message is delivered to {@code recipient}.
     * <p>
     * Waits a short settle window first. Delivery happens after commit on a separate executor, so
     * checking immediately would pass even for a message that was merely a few milliseconds behind,
     * and the test would then be asserting nothing at all. The window is deliberately short — a
     * second of real time per negative assertion — which trades a small residual chance of a false
     * pass for a suite that still runs quickly. A regression that starts sending mail here would
     * send it on the same path and within the same window as every positive assertion in this
     * suite, all of which arrive well inside it.
     */
    public static void assertNoMailFor(String recipient) {
        long deadline = System.currentTimeMillis() + NEGATIVE_ASSERTION_WINDOW_MS;
        while (System.currentTimeMillis() < deadline) {
            if (deliveredTo(recipient) > 0) {
                throw new AssertionError("Expected no email for %s, but one arrived. Received: %s"
                        .formatted(recipient, describeReceived()));
            }
            sleep(50);
        }
    }

    private static long deliveredTo(String recipient) {
        return Arrays.stream(SMTP.getReceivedMessages())
                .filter(message -> addressedTo(message, recipient))
                .count();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting on the test mailbox", e);
        }
    }

    private static Optional<String> findToken(String recipient, String linkPath) {
        return Arrays.stream(SMTP.getReceivedMessages())
                .filter(message -> addressedTo(message, recipient))
                .map(TestMailbox::decode)
                .filter(body -> body.contains(linkPath + "?token="))
                .map(body -> body.substring(body.indexOf(linkPath + "?token=")))
                .map(TOKEN::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .findFirst();
    }

    private static boolean addressedTo(MimeMessage message, String recipient) {
        try {
            return Arrays.stream(message.getAllRecipients())
                    .anyMatch(address -> address.toString().equalsIgnoreCase(recipient));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Renders the MIME source with quoted-printable encoding undone, so links are not split
     * across soft line breaks and {@code =} is not escaped as {@code =3D}.
     */
    private static String decode(MimeMessage message) {
        try {
            var buffer = new ByteArrayOutputStream();
            message.writeTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8)
                    .replace("=\r\n", "")
                    .replace("=\n", "")
                    .replace("=3D", "=");
        } catch (Exception e) {
            return "";
        }
    }

    private static int currentCount() {
        return SMTP.getReceivedMessages().length;
    }

    private static String describeReceived() {
        return Arrays.stream(SMTP.getReceivedMessages())
                .map(message -> {
                    try {
                        return message.getSubject() + " -> " + Arrays.toString(message.getAllRecipients());
                    } catch (Exception e) {
                        return "<unreadable>";
                    }
                })
                .toList()
                .toString();
    }
}

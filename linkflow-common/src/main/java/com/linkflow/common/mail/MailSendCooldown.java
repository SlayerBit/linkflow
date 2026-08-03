package com.linkflow.common.mail;

import com.linkflow.common.security.SecureTokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limits transactional email <em>per recipient address</em>.
 * <p>
 * The IP-based limiter in front of {@code /api/v1/auth/**} protects the application from a caller;
 * it does not protect a third party from the application. Resend-verification and forgot-password
 * both take an arbitrary address and cause mail to be sent to it, so at the IP limit of 200
 * requests per minute a single attacker can direct 200 emails per minute at someone else's inbox,
 * and rotating source addresses removes even that ceiling. The cost lands on the victim and on the
 * sending domain's reputation, neither of which the IP limiter can see.
 * <p>
 * Keying on the recipient is what closes that: the address is the resource being consumed, so it is
 * the thing worth counting.
 * <p>
 * There is no daily cap on top of the interval, which leaves a slow trickle possible. That is a
 * deliberate trade: a hard cap would eventually refuse a real person their own password reset, and
 * being unable to recover an account is a worse outcome than receiving extra mail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailSendCooldown {

    /**
     * Tracked independently, so a password reset is never refused because the same person asked for
     * a verification link a moment earlier. The two say nothing about each other.
     */
    public enum Purpose {
        EMAIL_VERIFICATION,
        PASSWORD_RESET,
        EMAIL_CHANGE
    }

    private static final String KEY_PREFIX = "mail:cooldown:";

    private final StringRedisTemplate redis;
    private final MailCooldownProperties properties;
    private final SecureTokenGenerator tokenGenerator;

    /**
     * Claims the right to send one email of {@code purpose} to {@code emailAddress}.
     *
     * @return {@code true} if the caller may send, {@code false} if an email of this kind went to
     *         this address within the configured interval
     */
    public boolean tryAcquire(Purpose purpose, String emailAddress) {
        Duration interval = properties.getInterval();
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return true;
        }

        try {
            Boolean claimed = redis.opsForValue()
                    .setIfAbsent(keyFor(purpose, emailAddress), "1", interval);

            // setIfAbsent returns null only when the command was pipelined or queued in a
            // transaction, which this is not. Treated as permission granted for the same reason the
            // catch below does.
            if (claimed == null) {
                return true;
            }
            if (!claimed) {
                log.debug("Suppressed {} email: address is within its {}s cooldown",
                        purpose, interval.toSeconds());
            }
            return claimed;
        } catch (Exception ex) {
            // Fails open. Redis being unavailable must not stand between someone and their own
            // account recovery — the worst case here is duplicate mail, while failing closed would
            // mean nobody can reset a password during a cache outage.
            log.warn("Mail cooldown check failed for purpose={}; allowing the send: {}",
                    purpose, ex.getMessage());
            return true;
        }
    }

    /**
     * Addresses are hashed rather than embedded in the key. This is not an attempt to make them
     * unrecoverable — an address is guessable and the hash is unsalted — but it keeps mailboxes out
     * of {@code KEYS} output, {@code MONITOR} traces, and Redis snapshots, where they would
     * otherwise sit in plaintext and outlive their usefulness.
     */
    private String keyFor(Purpose purpose, String emailAddress) {
        String normalized = emailAddress == null ? "" : emailAddress.trim().toLowerCase();
        return KEY_PREFIX + purpose.name() + ":" + tokenGenerator.hash(normalized);
    }
}

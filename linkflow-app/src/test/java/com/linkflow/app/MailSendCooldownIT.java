package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import com.linkflow.common.mail.MailCooldownProperties;
import com.linkflow.common.mail.MailSendCooldown;
import com.linkflow.common.security.SecureTokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the per-recipient mail throttle against the real Redis container.
 * <p>
 * The component is constructed here rather than injected because the shared test context disables
 * the cooldown — recovery flows are driven several times in a row by other suites, which no real
 * user would do inside a minute. Building it with its own properties keeps that convenience without
 * leaving the throttle itself untested.
 */
class MailSendCooldownIT extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private SecureTokenGenerator tokenGenerator;

    @Test
    void theSecondSendToAnAddressIsRefusedWithinTheInterval() {
        MailSendCooldown cooldown = cooldownWith(Duration.ofMinutes(1));
        String address = uniqueAddress();

        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, address));
        assertFalse(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, address));
    }

    @Test
    void theIntervalExpiresAndSendingResumes() throws Exception {
        MailSendCooldown cooldown = cooldownWith(Duration.ofSeconds(1));
        String address = uniqueAddress();

        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, address));
        assertFalse(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, address));

        // Redis expires the key; nothing in the application needs to sweep it.
        Thread.sleep(1_200);

        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, address));
    }

    /**
     * Someone who has just been sent an activation link may still legitimately need a password
     * reset. Sharing one counter between the two would refuse them the second on the strength of
     * the first, which says nothing about it.
     */
    @Test
    void purposesAreThrottledIndependently() {
        MailSendCooldown cooldown = cooldownWith(Duration.ofMinutes(1));
        String address = uniqueAddress();

        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.EMAIL_VERIFICATION, address));
        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, address));
        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.EMAIL_CHANGE, address));

        assertFalse(cooldown.tryAcquire(MailSendCooldown.Purpose.EMAIL_VERIFICATION, address));
    }

    @Test
    void oneAddressBeingThrottledDoesNotAffectAnother() {
        MailSendCooldown cooldown = cooldownWith(Duration.ofMinutes(1));

        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, uniqueAddress()));
        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, uniqueAddress()));
    }

    /**
     * Addresses are compared as mailboxes, not as strings, so varying the case or padding cannot be
     * used to buy another send.
     */
    @Test
    void addressesAreNormalisedBeforeThrottling() {
        MailSendCooldown cooldown = cooldownWith(Duration.ofMinutes(1));
        String address = uniqueAddress();

        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, address));
        assertFalse(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET,
                "  " + address.toUpperCase() + "  "));
    }

    /**
     * A zero interval turns the throttle off outright, which is what the rest of the suite relies
     * on and what a local development setup wants.
     */
    @Test
    void aZeroIntervalDisablesTheThrottleEntirely() {
        MailSendCooldown cooldown = cooldownWith(Duration.ZERO);
        String address = uniqueAddress();

        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, address));
        assertTrue(cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, address));
    }

    /**
     * Mailboxes must not sit in plaintext in Redis, where they would show up in {@code KEYS}
     * output, {@code MONITOR} traces, and snapshots.
     */
    @Test
    void theRecipientAddressIsNotStoredInTheKey() {
        MailSendCooldown cooldown = cooldownWith(Duration.ofMinutes(1));
        String address = uniqueAddress();

        cooldown.tryAcquire(MailSendCooldown.Purpose.PASSWORD_RESET, address);

        var keys = redis.keys("mail:cooldown:*");
        assertFalse(keys.isEmpty(), "expected the throttle to leave a key behind");
        assertTrue(keys.stream().noneMatch(key -> key.contains(address)),
                "the recipient address must not appear in the Redis key");
    }

    private MailSendCooldown cooldownWith(Duration interval) {
        MailCooldownProperties properties = new MailCooldownProperties();
        properties.setInterval(interval);
        return new MailSendCooldown(redis, properties, tokenGenerator);
    }

    private String uniqueAddress() {
        return "cooldown-" + System.nanoTime() + "@example.com";
    }
}

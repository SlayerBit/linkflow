package com.linkflow.auth.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtSecretValidatorTest {

    /** Random 64-byte key, matching what production is required to supply. */
    private static final String STRONG_SECRET =
            "giQ9CvmetipNcnL3ufIrZzK5fY2vaxgT8Jlhe2rBy7NdS98EVhmMxPQvFLaMrbXddEWgcXa5wdEXc4rayi7bTA==";

    @Test
    void hasWeakEntropy_detectsSequentialSecret() {
        byte[] weak = "Abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGH".getBytes(StandardCharsets.US_ASCII);
        assertTrue(JwtSecretValidator.hasWeakEntropy(weak));
    }

    @Test
    void hasWeakEntropy_acceptsRandomSecret() {
        byte[] decoded = Base64.getDecoder().decode(STRONG_SECRET);
        assertFalse(JwtSecretValidator.hasWeakEntropy(decoded));
    }

    @Test
    void startup_succeedsWithStrongSecretUnderProd() {
        assertDoesNotThrow(() -> validatorFor(STRONG_SECRET, "prod").validateSecretOnStartup());
    }

    @Test
    void startup_failsWhenSecretMissingUnderProd() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validatorFor("", "prod").validateSecretOnStartup());
        assertTrue(ex.getMessage().contains("must be set"));
    }

    @Test
    void startup_failsWhenSecretTooShortForHs512() {
        // 54 decoded bytes: enough for HS384, short of the 64 HS512 requires.
        String shortSecret = "dGVzdC1zZWNyZXQta2V5LWZvci1pbnRlZ3JhdGlvbi10ZXN0cy1taW5pbXVtLTY0LWNoYXJz";

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> validatorFor(shortSecret, "prod").validateSecretOnStartup());
        assertTrue(ex.getMessage().contains("at least 64 bytes"));
    }

    @Test
    void startup_failsOnNonBase64Secret() {
        assertThrows(IllegalStateException.class,
                () -> validatorFor("not valid base64 !!!", "prod").validateSecretOnStartup());
    }

    @Test
    void startup_skipsValidationOutsideProd() {
        assertDoesNotThrow(() -> validatorFor("", "dev").validateSecretOnStartup());
    }

    private JwtSecretValidator validatorFor(String secret, String activeProfile) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfile);

        return new JwtSecretValidator(properties, environment);
    }
}

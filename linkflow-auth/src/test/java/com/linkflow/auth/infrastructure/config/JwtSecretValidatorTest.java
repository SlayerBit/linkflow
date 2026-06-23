package com.linkflow.auth.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtSecretValidatorTest {

    @Test
    void hasWeakEntropy_detectsSequentialSecret() {
        byte[] weak = "Abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGH".getBytes(StandardCharsets.US_ASCII);
        assertTrue(JwtSecretValidator.hasWeakEntropy(weak));
    }

    @Test
    void hasWeakEntropy_acceptsRandomSecret() {
        byte[] strong = new byte[64];
        for (int i = 0; i < strong.length; i++) {
            strong[i] = (byte) (i * 7 + 13);
        }
        assertFalse(JwtSecretValidator.hasWeakEntropy(strong));
    }

    @Test
    void hasWeakEntropy_acceptsIntegrationTestSecret() {
        byte[] decoded = Base64.getDecoder().decode(
                "dGVzdC1zZWNyZXQta2V5LWZvci1pbnRlZ3JhdGlvbi10ZXN0cy1taW5pbXVtLTY0LWNoYXJz");
        assertFalse(JwtSecretValidator.hasWeakEntropy(decoded));
    }
}

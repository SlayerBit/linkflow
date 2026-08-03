package com.linkflow.auth.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Refuses to start the prod profile when the JWT secret is missing, undersized, or low entropy.
 * <p>
 * Runs during context refresh rather than on {@code ApplicationReadyEvent} so the failure happens
 * before the HTTP connector accepts traffic — a misconfigured instance should never serve a single
 * request rather than briefly issuing tokens under a weak key.
 */
@Component
@RequiredArgsConstructor
public class JwtSecretValidator implements InitializingBean {

    /**
     * HS512 signs with a 512-bit key, and jjwt rejects anything shorter. Matching that here turns
     * an obscure WeakKeyException deep in token issuance into a clear startup message.
     */
    private static final int MIN_SECRET_BYTES = 64;

    private static final double MIN_UNIQUE_BYTE_RATIO = 0.25;

    private final JwtProperties jwtProperties;
    private final Environment environment;

    @Override
    public void afterPropertiesSet() {
        validateSecretOnStartup();
    }

    void validateSecretOnStartup() {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return;
        }
        String secret = jwtProperties.getSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "LINKFLOW_JWT_SECRET must be set when running with the prod profile. "
                            + "Generate one with: openssl rand -base64 64");
        }
        byte[] decoded;
        try {
            decoded = java.util.Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "LINKFLOW_JWT_SECRET must be a valid Base64-encoded key. "
                            + "Generate one with: openssl rand -base64 64",
                    ex);
        }
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "LINKFLOW_JWT_SECRET must decode to at least " + MIN_SECRET_BYTES
                            + " bytes for HS512 signing, but decoded to " + decoded.length
                            + ". Note this is the decoded length, not the length of the Base64 "
                            + "string. Generate one with: openssl rand -base64 64");
        }
        if (hasWeakEntropy(decoded)) {
            throw new IllegalStateException(
                    "LINKFLOW_JWT_SECRET has insufficient entropy; it looks like encoded text "
                            + "rather than random bytes. Generate one with: openssl rand -base64 64");
        }
    }

    static boolean hasWeakEntropy(byte[] decoded) {
        Set<Byte> uniqueBytes = new HashSet<>();
        for (byte b : decoded) {
            uniqueBytes.add(b);
        }
        double ratio = (double) uniqueBytes.size() / decoded.length;
        if (ratio < MIN_UNIQUE_BYTE_RATIO) {
            return true;
        }
        String asString = new String(decoded, java.nio.charset.StandardCharsets.US_ASCII);
        return isSequentialPattern(asString);
    }

    private static boolean isSequentialPattern(String value) {
        String lower = value.toLowerCase();
        return lower.contains("abcdefghijklmnopqrstuvwxyz")
                || lower.contains("0123456789")
                || lower.chars().distinct().count() <= 8;
    }
}

package com.linkflow.auth.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Fails fast when the prod profile is active and JWT secret is missing, too short, or weak.
 */
@Component
@RequiredArgsConstructor
public class JwtSecretValidator {

    private static final int MIN_SECRET_BYTES = 32;
    private static final double MIN_UNIQUE_BYTE_RATIO = 0.25;

    private final JwtProperties jwtProperties;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    void validateSecretOnStartup() {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return;
        }
        String secret = jwtProperties.getSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                    "LINKFLOW_JWT_SECRET must be set when running with the prod profile");
        }
        byte[] decoded;
        try {
            decoded = java.util.Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "LINKFLOW_JWT_SECRET must be a valid Base64-encoded key (minimum 32 bytes decoded)",
                    ex);
        }
        if (decoded.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "LINKFLOW_JWT_SECRET must decode to at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (hasWeakEntropy(decoded)) {
            throw new IllegalStateException(
                    "LINKFLOW_JWT_SECRET has insufficient entropy; use a cryptographically random 64-byte secret");
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

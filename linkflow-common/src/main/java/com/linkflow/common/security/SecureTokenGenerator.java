package com.linkflow.common.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates and hashes single-use opaque tokens for email verification, password reset,
 * email change, and refresh tokens.
 * <p>
 * Only the SHA-256 hash is ever persisted, so a database disclosure does not yield usable
 * tokens. Tokens carry 384 bits of entropy, which is far beyond brute-force reach and removes
 * any need for rate limiting on token lookup.
 * <p>
 * SHA-256 is appropriate here — unlike passwords, these tokens are high-entropy random values,
 * so a slow KDF would add latency without adding meaningful resistance.
 */
@Component
public class SecureTokenGenerator {

    private static final int TOKEN_BYTES = 48;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @return a URL-safe token suitable for embedding in an email link without encoding
     */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * @return lowercase hex SHA-256 digest of the token, for storage and lookup
     */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable in this JVM", e);
        }
    }
}

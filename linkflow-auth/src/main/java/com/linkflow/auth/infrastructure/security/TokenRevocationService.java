package com.linkflow.auth.infrastructure.security;

import com.linkflow.auth.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed access token revocation using a per-user "revoked after" timestamp.
 * Tokens issued before this timestamp are rejected even if cryptographically valid.
 */
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String KEY_PREFIX = "auth:user-revoked-after:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    public void markUserRevokedAfter(UUID userId, Instant revokedAfter) {
        String key = KEY_PREFIX + userId;
        stringRedisTemplate.opsForValue().set(
                key,
                String.valueOf(revokedAfter.toEpochMilli()),
                Duration.ofMillis(jwtProperties.getAccessExpirationMs() + 60_000L)
        );
    }

    public Optional<Instant> getUserRevokedAfter(UUID userId) {
        String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + userId);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Instant.ofEpochMilli(Long.parseLong(value)));
    }

    public boolean isTokenRevoked(UUID userId, Instant issuedAt) {
        return getUserRevokedAfter(userId)
                .map(revokedAfter -> !issuedAt.isAfter(revokedAfter))
                .orElse(false);
    }
}

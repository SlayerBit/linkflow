package com.linkflow.auth.application.service;

import com.linkflow.auth.domain.entity.RefreshToken;
import com.linkflow.auth.domain.exception.TokenExpiredException;
import com.linkflow.auth.domain.exception.TokenRevokedException;
import com.linkflow.auth.domain.repository.RefreshTokenRepository;
import com.linkflow.auth.infrastructure.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Service for managing opaque refresh tokens with rotation and revocation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Create a new opaque refresh token for the given user.
     * The raw token is returned; only its SHA-256 hash is stored.
     */
    @Transactional
    public String createRefreshToken(UUID userId) {
        String rawToken = generateOpaqueToken();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .userId(userId)
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshExpirationMs()))
                .build();

        refreshTokenRepository.save(refreshToken);
        log.debug("Refresh token created for userId={}", userId);
        return rawToken;
    }

    /**
     * Rotate a refresh token: revoke the old one, create a new one.
     * Returns the new raw refresh token.
     */
    @Transactional
    public RotationResult rotateRefreshToken(String rawOldToken) {
        String oldHash = hashToken(rawOldToken);

        RefreshToken oldToken = refreshTokenRepository.findByTokenHash(oldHash)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found during rotation");
                    return new TokenRevokedException();
                });

        if (oldToken.isRevoked()) {
            log.warn("Attempted reuse of revoked refresh token for userId={}", oldToken.getUserId());
            // Revoke all tokens for this user as a security measure
            refreshTokenRepository.revokeAllByUserId(oldToken.getUserId());
            throw new TokenRevokedException();
        }

        if (oldToken.isExpired()) {
            throw new TokenExpiredException();
        }

        // Create new token
        String newRawToken = generateOpaqueToken();
        String newHash = hashToken(newRawToken);

        // Revoke old token
        oldToken.revoke();
        oldToken.setReplacedByTokenHash(newHash);
        refreshTokenRepository.save(oldToken);

        // Save new token
        RefreshToken newToken = RefreshToken.builder()
                .tokenHash(newHash)
                .userId(oldToken.getUserId())
                .expiresAt(Instant.now().plusMillis(jwtProperties.getRefreshExpirationMs()))
                .build();
        refreshTokenRepository.save(newToken);

        log.debug("Refresh token rotated for userId={}", oldToken.getUserId());
        return new RotationResult(newRawToken, oldToken.getUserId());
    }

    /**
     * Revoke a refresh token (logout).
     */
    @Transactional
    public java.util.Optional<UUID> revokeRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .map(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                    log.info("Refresh token revoked for userId={}", token.getUserId());
                    return token.getUserId();
                });
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("All refresh tokens revoked for userId={}", userId);
    }

    @Transactional
    public int cleanupExpiredAndRevoked(java.time.Duration revokedRetention) {
        Instant revokedCutoff = Instant.now().minus(revokedRetention);
        int deleted = refreshTokenRepository.deleteExpiredOrRevoked(revokedCutoff, Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired/revoked refresh tokens", deleted);
        }
        return deleted;
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record RotationResult(String newRawToken, UUID userId) {
    }
}

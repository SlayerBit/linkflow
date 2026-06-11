package com.linkflow.auth.application.service;

import com.linkflow.auth.domain.entity.RefreshToken;
import com.linkflow.auth.domain.exception.TokenRevokedException;
import com.linkflow.auth.domain.repository.RefreshTokenRepository;
import com.linkflow.auth.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setRefreshExpirationMs(86_400_000L);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, properties);
    }

    @Test
    void rotateRefreshToken_revokesOldAndIssuesNew() {
        UUID userId = UUID.randomUUID();
        String rawOld = "old-refresh-token-value";
        String oldHash = RefreshTokenService.hashToken(rawOld);

        RefreshToken existing = RefreshToken.builder()
                .tokenHash(oldHash)
                .userId(userId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.RotationResult result = refreshTokenService.rotateRefreshToken(rawOld);

        assertNotNull(result.newRawToken());
        assertEquals(userId, result.userId());
        assertTrue(existing.isRevoked());
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void rotateRefreshToken_unknownToken_throwsRevoked() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThrows(TokenRevokedException.class, () -> refreshTokenService.rotateRefreshToken("unknown"));
    }

    @Test
    void rotateRefreshToken_reuseRevokedToken_revokesAllForUser() {
        UUID userId = UUID.randomUUID();
        String rawOld = "reused-refresh-token";
        String oldHash = RefreshTokenService.hashToken(rawOld);

        RefreshToken revoked = RefreshToken.builder()
                .tokenHash(oldHash)
                .userId(userId)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(revoked));

        assertThrows(TokenRevokedException.class, () -> refreshTokenService.rotateRefreshToken(rawOld));
        verify(refreshTokenRepository).revokeAllByUserId(userId);
    }
}

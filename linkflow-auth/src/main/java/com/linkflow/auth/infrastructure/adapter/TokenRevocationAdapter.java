package com.linkflow.auth.infrastructure.adapter;

import com.linkflow.auth.application.service.RefreshTokenService;
import com.linkflow.auth.infrastructure.security.TokenRevocationService;
import com.linkflow.common.port.TokenRevocationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TokenRevocationAdapter implements TokenRevocationPort {

    private final RefreshTokenService refreshTokenService;
    private final TokenRevocationService tokenRevocationService;

    @Override
    public void revokeAllRefreshTokensForUser(UUID userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    @Override
    public void markAccessTokensRevokedAfter(UUID userId, Instant revokedAfter) {
        tokenRevocationService.markUserRevokedAfter(userId, revokedAfter);
    }
}

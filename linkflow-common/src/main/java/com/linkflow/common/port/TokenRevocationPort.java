package com.linkflow.common.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Port for revoking user sessions. Implemented by linkflow-auth;
 * consumed by linkflow-user when disabling or deleting accounts.
 */
public interface TokenRevocationPort {

    void revokeAllRefreshTokensForUser(UUID userId);

    void markAccessTokensRevokedAfter(UUID userId, Instant revokedAfter);
}

package com.linkflow.auth.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "linkflow.jwt")
public class JwtProperties {

    private String secret;
    private long accessExpirationMs = 900_000; // 15 minutes
    private long refreshExpirationMs = 2_592_000_000L; // 30 days

    /**
     * Value placed in, and required of, the {@code iss} claim. Rejecting tokens minted by another
     * issuer means a signing key shared with a sibling service cannot be used to forge access here.
     */
    private String issuer = "linkflow";

    /**
     * Value placed in, and required of, the {@code aud} claim. Prevents a token issued for another
     * audience from being replayed against this API.
     */
    private String audience = "linkflow-api";

    /**
     * Tolerance for clock drift between instances when checking exp and nbf. Small on purpose:
     * generous skew extends the usable life of a token past its stated expiry.
     */
    private long clockSkewSeconds = 30;
}

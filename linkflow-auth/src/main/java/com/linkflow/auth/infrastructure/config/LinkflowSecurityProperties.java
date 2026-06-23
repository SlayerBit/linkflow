package com.linkflow.auth.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "linkflow.security")
public class LinkflowSecurityProperties {

    /**
     * When true, Swagger UI and OpenAPI docs are reachable without authentication.
     */
    private boolean swaggerPublic = true;

    /**
     * When true, all actuator endpoints are public (development default).
     * When false, only health endpoints are public unless metricsPublic is true.
     */
    private boolean actuatorPublic = true;

    /**
     * When true and actuatorPublic is false, Prometheus and metrics endpoints remain public
     * for internal scrapers (e.g. Docker Compose demo stack).
     */
    private boolean metricsPublic = false;

    /**
     * When true, email verification is required to log in.
     */
    private boolean emailVerificationRequired = true;

    /**
     * When true, opaque tokens (verification, password reset) are included in API responses.
     * Intended for local/demo environments only; must remain false in production.
     */
    private boolean exposeDevTokens = false;
}

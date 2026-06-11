package com.linkflow.ratelimit.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "linkflow.rate-limit")
public class RateLimitProperties {

    private int userRpm = 100;
    private int ipRpm = 200;

    /**
     * When true, auth endpoints fail closed (503) if Redis is unavailable.
     */
    private boolean authFailClosed = true;
}

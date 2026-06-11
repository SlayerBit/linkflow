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
}

package com.linkflow.web.dto.url;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UrlResponse(
        UUID id,
        String shortCode,
        String shortUrl,
        String originalUrl,
        Instant expiresAt,
        boolean active,
        Instant createdAt
) {
}

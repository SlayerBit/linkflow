package com.linkflow.web.dto.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UrlAnalyticsResponse(
        UUID shortUrlId,
        String shortCode,
        long totalClicks,
        Instant lastAccessedAt
) {
}

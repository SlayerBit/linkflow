package com.linkflow.web.dto.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TopUrlResponse(
        UUID shortUrlId,
        String shortCode,
        long totalClicks
) {
}

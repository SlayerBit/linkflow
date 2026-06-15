package com.linkflow.web.dto.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemStatsResponse(
        long totalUsers,
        long totalUrls,
        long totalClicks,
        long activeUrls,
        long inactiveUrls,
        long expiredUrls,
        long deletedUrls
) {
}

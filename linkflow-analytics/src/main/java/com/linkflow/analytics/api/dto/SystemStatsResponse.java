package com.linkflow.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SystemStatsResponse {

    private final long totalUsers;
    private final long totalUrls;
    private final long totalClicks;
    private final long activeUrls;
    private final long inactiveUrls;
    private final long expiredUrls;
    private final long deletedUrls;
}

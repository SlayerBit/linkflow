package com.linkflow.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class ClickEventResponse {
    private final UUID id;
    private final Instant clickedAt;
    private final String ipAddress;
    private final String userAgent;
    private final String referer;
}

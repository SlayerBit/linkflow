package com.linkflow.web.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClickEventResponse {
    private UUID id;
    private UUID shortUrlId;
    private String shortCode;
    private Instant clickedAt;
    private String ipAddress;
    private String userAgent;
    private String referer;
}

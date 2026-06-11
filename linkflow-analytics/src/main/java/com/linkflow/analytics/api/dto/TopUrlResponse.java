package com.linkflow.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class TopUrlResponse {

    private final UUID shortUrlId;
    private final String shortCode;
    private final long totalClicks;
}

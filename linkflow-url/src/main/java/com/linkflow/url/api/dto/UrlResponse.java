package com.linkflow.url.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class UrlResponse {

    private final UUID id;
    private final String shortCode;
    private final String shortUrl;
    private final String originalUrl;
    private final Instant expiresAt;
    private final boolean active;
    private final Instant createdAt;
}

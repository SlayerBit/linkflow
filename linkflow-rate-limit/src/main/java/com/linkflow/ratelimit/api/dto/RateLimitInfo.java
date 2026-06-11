package com.linkflow.ratelimit.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RateLimitInfo {

    private final long limit;
    private final long remaining;
    private final long reset;
    private final boolean allowed;
    @Builder.Default
    private final boolean backendUnavailable = false;

    public static RateLimitInfo failOpen(long limit, long reset) {
        return RateLimitInfo.builder()
                .limit(limit)
                .remaining(limit)
                .reset(reset)
                .allowed(true)
                .build();
    }

    public static RateLimitInfo failClosed(long limit, long reset) {
        return RateLimitInfo.builder()
                .limit(limit)
                .remaining(0)
                .reset(reset)
                .allowed(false)
                .backendUnavailable(true)
                .build();
    }
}

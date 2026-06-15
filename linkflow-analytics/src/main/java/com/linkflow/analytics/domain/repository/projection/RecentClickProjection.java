package com.linkflow.analytics.domain.repository.projection;

import java.time.Instant;
import java.util.UUID;

public interface RecentClickProjection {
    UUID getId();
    UUID getShortUrlId();
    String getShortCode();
    Instant getClickedAt();
    String getIpAddress();
    String getUserAgent();
    String getReferer();
}

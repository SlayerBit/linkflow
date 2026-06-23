package com.linkflow.url.domain.repository.projection;

import java.util.UUID;

public interface TopUrlStatsProjection {
    UUID getShortUrlId();
    String getShortCode();
    long getTotalClicks();
}

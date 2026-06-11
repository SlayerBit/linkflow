package com.linkflow.analytics.domain.repository.projection;

import java.util.UUID;

public interface TopUrlProjection {

    UUID getShortUrlId();

    String getShortCode();

    Long getTotalClicks();
}

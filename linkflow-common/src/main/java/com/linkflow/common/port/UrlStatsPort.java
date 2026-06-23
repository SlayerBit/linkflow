package com.linkflow.common.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for URL metadata and aggregate statistics. Implemented by linkflow-url;
 * consumed by linkflow-analytics to avoid cross-module table queries.
 */
public interface UrlStatsPort {

    long countTotalUrls();

    long countActiveUrls();

    long countInactiveUrls();

    long countExpiredUrls();

    long countDeletedUrls();

    Optional<UUID> findOwnerIdByShortUrlId(UUID shortUrlId);

    Optional<String> findShortCodeByShortUrlId(UUID shortUrlId);

    List<TopUrlData> findTopByOwnerId(UUID ownerId, int limit);

    List<TopUrlData> findTopSystemWide(int limit);

    record TopUrlData(UUID shortUrlId, String shortCode, long totalClicks) {}
}

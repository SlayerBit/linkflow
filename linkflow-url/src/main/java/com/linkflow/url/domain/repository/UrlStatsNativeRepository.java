package com.linkflow.url.domain.repository;

import com.linkflow.url.domain.entity.ShortUrl;
import com.linkflow.url.domain.repository.projection.TopUrlStatsProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Repository
public interface UrlStatsNativeRepository extends Repository<ShortUrl, UUID> {

    @Query(value = "SELECT COUNT(*) FROM short_urls WHERE deleted = false", nativeQuery = true)
    long countTotalUrls();

    @Query(value = """
            SELECT COUNT(*) FROM short_urls
            WHERE deleted = false AND active = true
              AND (expires_at IS NULL OR expires_at >= NOW())
            """, nativeQuery = true)
    long countActiveUrls();

    @Query(value = """
            SELECT COUNT(*) FROM short_urls
            WHERE deleted = false AND active = false
              AND (expires_at IS NULL OR expires_at >= NOW())
            """, nativeQuery = true)
    long countInactiveUrls();

    @Query(value = """
            SELECT COUNT(*) FROM short_urls
            WHERE deleted = false AND expires_at IS NOT NULL AND expires_at < NOW()
            """, nativeQuery = true)
    long countExpiredUrls();

    @Query(value = "SELECT COUNT(*) FROM short_urls WHERE deleted = true", nativeQuery = true)
    long countDeletedUrls();

    @Query(value = """
            SELECT su.owner_id
            FROM short_urls su
            WHERE su.id = :shortUrlId AND su.deleted = false
            """, nativeQuery = true)
    Optional<UUID> findOwnerIdByShortUrlId(@Param("shortUrlId") UUID shortUrlId);

    @Query(value = """
            SELECT su.short_code
            FROM short_urls su
            WHERE su.id = :shortUrlId AND su.deleted = false
            """, nativeQuery = true)
    Optional<String> findShortCodeByShortUrlId(@Param("shortUrlId") UUID shortUrlId);

    @Query(value = """
            SELECT su.id AS shortUrlId, su.short_code AS shortCode, ua.total_clicks AS totalClicks
            FROM short_urls su
            INNER JOIN url_analytics ua ON ua.short_url_id = su.id
            WHERE su.owner_id = :ownerId AND su.deleted = false
            ORDER BY ua.total_clicks DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopUrlStatsProjection> findTopByOwnerId(@Param("ownerId") UUID ownerId, @Param("limit") int limit);

    @Query(value = """
            SELECT su.id AS shortUrlId, su.short_code AS shortCode, ua.total_clicks AS totalClicks
            FROM short_urls su
            INNER JOIN url_analytics ua ON ua.short_url_id = su.id
            WHERE su.deleted = false
            ORDER BY ua.total_clicks DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopUrlStatsProjection> findTopSystemWide(@Param("limit") int limit);
}

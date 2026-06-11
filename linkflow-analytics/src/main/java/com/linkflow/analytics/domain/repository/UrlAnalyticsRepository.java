package com.linkflow.analytics.domain.repository;

import com.linkflow.analytics.domain.entity.UrlAnalytics;
import com.linkflow.analytics.domain.repository.projection.TopUrlProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UrlAnalyticsRepository extends JpaRepository<UrlAnalytics, UUID> {

    Optional<UrlAnalytics> findByShortUrlId(UUID shortUrlId);

    @Query(value = """
            SELECT su.id AS shortUrlId, su.short_code AS shortCode, ua.total_clicks AS totalClicks
            FROM short_urls su
            INNER JOIN url_analytics ua ON ua.short_url_id = su.id
            WHERE su.owner_id = :ownerId AND su.deleted = false
            ORDER BY ua.total_clicks DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopUrlProjection> findTopByOwnerId(@Param("ownerId") UUID ownerId, @Param("limit") int limit);

    @Query(value = """
            SELECT su.id AS shortUrlId, su.short_code AS shortCode, ua.total_clicks AS totalClicks
            FROM short_urls su
            INNER JOIN url_analytics ua ON ua.short_url_id = su.id
            WHERE su.deleted = false
            ORDER BY ua.total_clicks DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<TopUrlProjection> findTopSystemWide(@Param("limit") int limit);

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
}

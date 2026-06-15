package com.linkflow.analytics.domain.repository;

import com.linkflow.analytics.domain.entity.ClickEvent;
import com.linkflow.analytics.domain.repository.projection.ClickTrendProjection;
import com.linkflow.analytics.domain.repository.projection.RecentClickProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID> {

    List<ClickEvent> findByShortUrlIdOrderByClickedAtDesc(UUID shortUrlId, Pageable pageable);

    @Query(value = """
            SELECT CAST(ce.clicked_at AS date) AS clickDate, COUNT(*) AS clickCount
            FROM click_events ce
            WHERE ce.short_url_id = :shortUrlId AND ce.clicked_at >= :startDate
            GROUP BY CAST(ce.clicked_at AS date)
            ORDER BY clickDate ASC
            """, nativeQuery = true)
    List<ClickTrendProjection> findClickTrendByUrl(@Param("shortUrlId") UUID shortUrlId, @Param("startDate") Instant startDate);

    @Query(value = """
            SELECT CAST(ce.clicked_at AS date) AS clickDate, COUNT(*) AS clickCount
            FROM click_events ce
            WHERE ce.clicked_at >= :startDate
            GROUP BY CAST(ce.clicked_at AS date)
            ORDER BY clickDate ASC
            """, nativeQuery = true)
    List<ClickTrendProjection> findSystemClickTrend(@Param("startDate") Instant startDate);

    @Query(value = """
            SELECT ce.id AS id, ce.short_url_id AS shortUrlId, su.short_code AS shortCode,
                   ce.clicked_at AS clickedAt, ce.ip_address AS ipAddress,
                   ce.user_agent AS userAgent, ce.referer AS referer
            FROM click_events ce
            INNER JOIN short_urls su ON ce.short_url_id = su.id
            WHERE su.owner_id = :ownerId AND su.deleted = false
            ORDER BY ce.clicked_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<RecentClickProjection> findRecentClicksProjectionByOwnerId(@Param("ownerId") UUID ownerId, @Param("limit") int limit);

    @Query(value = """
            SELECT ce.id AS id, ce.short_url_id AS shortUrlId, su.short_code AS shortCode,
                   ce.clicked_at AS clickedAt, ce.ip_address AS ipAddress,
                   ce.user_agent AS userAgent, ce.referer AS referer
            FROM click_events ce
            INNER JOIN short_urls su ON ce.short_url_id = su.id
            WHERE su.deleted = false
            ORDER BY ce.clicked_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<RecentClickProjection> findRecentClicksProjectionSystemWide(@Param("limit") int limit);
}

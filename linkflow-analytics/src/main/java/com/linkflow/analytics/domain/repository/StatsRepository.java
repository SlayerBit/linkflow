package com.linkflow.analytics.domain.repository;

import com.linkflow.analytics.domain.entity.UrlAnalytics;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
public interface StatsRepository extends Repository<UrlAnalytics, java.util.UUID> {

    @Query(value = "SELECT COUNT(*) FROM users WHERE deleted = false", nativeQuery = true)
    long countActiveUsers();

    @Query(value = "SELECT COUNT(*) FROM short_urls WHERE deleted = false", nativeQuery = true)
    long countTotalUrls();

    @Query(value = "SELECT COUNT(*) FROM short_urls WHERE deleted = false AND active = true AND (expires_at IS NULL OR expires_at >= NOW())", nativeQuery = true)
    long countActiveUrls();

    @Query(value = "SELECT COUNT(*) FROM short_urls WHERE deleted = false AND active = false AND (expires_at IS NULL OR expires_at >= NOW())", nativeQuery = true)
    long countInactiveUrls();

    @Query(value = "SELECT COUNT(*) FROM short_urls WHERE deleted = false AND expires_at IS NOT NULL AND expires_at < NOW()", nativeQuery = true)
    long countExpiredUrls();

    @Query(value = "SELECT COUNT(*) FROM short_urls WHERE deleted = true", nativeQuery = true)
    long countDeletedUrls();

    @Query(value = "SELECT COALESCE(SUM(total_clicks), 0) FROM url_analytics", nativeQuery = true)
    long countTotalClicks();
}

package com.linkflow.analytics.domain.repository;

import com.linkflow.analytics.domain.entity.UrlAnalytics;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
public interface StatsRepository extends Repository<UrlAnalytics, java.util.UUID> {

    @Query(value = "SELECT COALESCE(SUM(total_clicks), 0) FROM url_analytics", nativeQuery = true)
    long countTotalClicks();
}

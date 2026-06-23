package com.linkflow.analytics.domain.repository;

import com.linkflow.analytics.domain.entity.UrlAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UrlAnalyticsRepository extends JpaRepository<UrlAnalytics, UUID> {

    Optional<UrlAnalytics> findByShortUrlId(UUID shortUrlId);
}

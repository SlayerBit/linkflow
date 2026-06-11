package com.linkflow.analytics.domain.entity;

import com.linkflow.common.audit.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "url_analytics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrlAnalytics extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "short_url_id", nullable = false, unique = true)
    private UUID shortUrlId;

    @Builder.Default
    @Column(name = "total_clicks", nullable = false)
    private long totalClicks = 0L;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;
}

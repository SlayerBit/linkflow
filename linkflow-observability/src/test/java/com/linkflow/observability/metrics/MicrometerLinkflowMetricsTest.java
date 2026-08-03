package com.linkflow.observability.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrometerLinkflowMetricsTest {

    private SimpleMeterRegistry registry;
    private MicrometerLinkflowMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerLinkflowMetrics(registry);
    }

    @Test
    void recordsRedirectAndCacheOutcomes() {
        metrics.redirectResolved("hit");
        metrics.redirectRejected("expired");
        metrics.urlCacheLookup("miss");

        assertEquals(1.0, registry.get("linkflow.redirects")
                .tag("result", "success").tag("cache", "hit").counter().count());
        assertEquals(1.0, registry.get("linkflow.redirects")
                .tag("result", "expired").tag("cache", "none").counter().count());
        assertEquals(1.0, registry.get("linkflow.url_cache")
                .tag("result", "miss").counter().count());
    }

    @Test
    void recordsAuthAndUrlCreation() {
        metrics.loginSucceeded();
        metrics.loginFailed("email_not_verified");
        metrics.registrationSucceeded();
        metrics.urlsCreated(3, true);

        assertEquals(1.0, registry.get("linkflow.auth.login")
                .tag("result", "success").counter().count());
        assertEquals(1.0, registry.get("linkflow.auth.login")
                .tag("result", "email_not_verified").counter().count());
        assertEquals(1.0, registry.get("linkflow.auth.register").counter().count());
        assertEquals(3.0, registry.get("linkflow.url.creations")
                .tag("alias", "custom").counter().count());
    }

    @Test
    void recordsRateLimitAndAnalytics() {
        metrics.rateLimitExceeded("ip");
        metrics.rateLimitBackendUnavailable("user");
        metrics.analyticsFlush("click_events", 10);
        metrics.analyticsFlushFailed();

        assertEquals(1.0, registry.get("linkflow.rate_limit.exceeded")
                .tag("dimension", "ip").counter().count());
        assertEquals(1.0, registry.get("linkflow.rate_limit.backend_unavailable")
                .tag("dimension", "user").counter().count());
        assertEquals(10.0, registry.get("linkflow.analytics.flush.records")
                .tag("kind", "click_events").counter().count());
        assertEquals(1.0, registry.get("linkflow.analytics.flush.cycles")
                .tag("kind", "all").tag("result", "failure").counter().count());
    }

    @Test
    void sanitisesUnexpectedTagValues() {
        metrics.loginFailed("Weird Reason!!");

        assertEquals(1.0, registry.get("linkflow.auth.login")
                .tag("result", "weird_reason__").counter().count());
    }
}

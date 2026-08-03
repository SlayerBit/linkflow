package com.linkflow.observability.metrics;

import com.linkflow.common.metrics.LinkflowMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer-backed {@link LinkflowMetrics}. Counters use the {@code linkflow.} prefix so they
 * sit next to {@code linkflow.info} in Prometheus and stay distinct from JVM/HTTP auto-metrics.
 */
@Component
public class MicrometerLinkflowMetrics implements LinkflowMetrics {

    private final MeterRegistry registry;

    public MicrometerLinkflowMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void redirectResolved(String cacheOutcome) {
        Counter.builder("linkflow.redirects")
                .description("Short-URL redirect outcomes")
                .tag("result", "success")
                .tag("cache", sanitize(cacheOutcome))
                .register(registry)
                .increment();
    }

    @Override
    public void redirectRejected(String reason) {
        Counter.builder("linkflow.redirects")
                .description("Short-URL redirect outcomes")
                .tag("result", sanitize(reason))
                .tag("cache", "none")
                .register(registry)
                .increment();
    }

    @Override
    public void urlCacheLookup(String result) {
        Counter.builder("linkflow.url_cache")
                .description("URL redirect cache lookups")
                .tag("result", sanitize(result))
                .register(registry)
                .increment();
    }

    @Override
    public void urlsCreated(int count, boolean customAlias) {
        if (count <= 0) {
            return;
        }
        // Named linkflow.url.creations (not linkflow.urls.created): Micrometer's Prometheus
        // naming treats a trailing "created" segment as a statistic suffix and collapses it into
        // _total, which would publish linkflow_urls_total and hide the intent of the metric.
        Counter.builder("linkflow.url.creations")
                .description("Short URLs created")
                .tag("alias", customAlias ? "custom" : "generated")
                .register(registry)
                .increment(count);
    }

    @Override
    public void loginSucceeded() {
        Counter.builder("linkflow.auth.login")
                .description("Login attempts")
                .tag("result", "success")
                .register(registry)
                .increment();
    }

    @Override
    public void loginFailed(String reason) {
        Counter.builder("linkflow.auth.login")
                .description("Login attempts")
                .tag("result", sanitize(reason))
                .register(registry)
                .increment();
    }

    @Override
    public void registrationSucceeded() {
        Counter.builder("linkflow.auth.register")
                .description("Successful registrations")
                .register(registry)
                .increment();
    }

    @Override
    public void rateLimitExceeded(String dimension) {
        Counter.builder("linkflow.rate_limit.exceeded")
                .description("Requests rejected by the application rate limiter")
                .tag("dimension", sanitize(dimension))
                .register(registry)
                .increment();
    }

    @Override
    public void rateLimitBackendUnavailable(String dimension) {
        Counter.builder("linkflow.rate_limit.backend_unavailable")
                .description("Rate-limit checks that failed because Redis was unavailable (fail-closed)")
                .tag("dimension", sanitize(dimension))
                .register(registry)
                .increment();
    }

    @Override
    public void analyticsFlush(String kind, int records) {
        if (records <= 0) {
            return;
        }
        Counter.builder("linkflow.analytics.flush.records")
                .description("Analytics records flushed from Redis to PostgreSQL")
                .tag("kind", sanitize(kind))
                .register(registry)
                .increment(records);
        Counter.builder("linkflow.analytics.flush.cycles")
                .description("Analytics flush cycles that persisted at least one record")
                .tag("kind", sanitize(kind))
                .tag("result", "success")
                .register(registry)
                .increment();
    }

    @Override
    public void analyticsFlushFailed() {
        Counter.builder("linkflow.analytics.flush.cycles")
                .description("Analytics flush cycles that persisted at least one record")
                .tag("kind", "all")
                .tag("result", "failure")
                .register(registry)
                .increment();
    }

    /**
     * Micrometer tags must be low-cardinality. Anything unexpected collapses to {@code unknown}
     * rather than leaking free-form strings into the time series.
     */
    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }
}

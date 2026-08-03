package com.linkflow.common.metrics;

/**
 * Application-level business metrics, kept free of Micrometer so feature modules do not depend on
 * the metrics library.
 * <p>
 * The production implementation lives in {@code linkflow-observability}. Unit tests and modules
 * that construct services by hand use {@link #noop()}.
 */
public interface LinkflowMetrics {

    /** Cache outcome for a successful redirect: {@code hit}, {@code stale}, or {@code miss}. */
    void redirectResolved(String cacheOutcome);

    /** Redirect rejected: {@code not_found}, {@code expired}, or {@code deactivated}. */
    void redirectRejected(String reason);

    /** URL cache lookup result: {@code hit}, {@code miss}, {@code negative}, or {@code error}. */
    void urlCacheLookup(String result);

    /** One or more short URLs persisted. */
    void urlsCreated(int count, boolean customAlias);

    void loginSucceeded();

    /**
     * Login rejected. {@code reason} is a stable label such as {@code invalid_credentials},
     * {@code email_not_verified}, or {@code disabled} — never a user-supplied string.
     */
    void loginFailed(String reason);

    void registrationSucceeded();

    /** Dimension is {@code user} or {@code ip}. */
    void rateLimitExceeded(String dimension);

    /** Dimension is {@code user} or {@code ip}. */
    void rateLimitBackendUnavailable(String dimension);

    /** Kind is {@code click_events} or {@code counters}. */
    void analyticsFlush(String kind, int records);

    void analyticsFlushFailed();

    static LinkflowMetrics noop() {
        return NoOpLinkflowMetrics.INSTANCE;
    }
}

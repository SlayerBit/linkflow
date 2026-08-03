package com.linkflow.common.metrics;

/**
 * Silent metrics sink for unit tests and any context that has not loaded linkflow-observability.
 */
final class NoOpLinkflowMetrics implements LinkflowMetrics {

    static final NoOpLinkflowMetrics INSTANCE = new NoOpLinkflowMetrics();

    private NoOpLinkflowMetrics() {
    }

    @Override
    public void redirectResolved(String cacheOutcome) {
    }

    @Override
    public void redirectRejected(String reason) {
    }

    @Override
    public void urlCacheLookup(String result) {
    }

    @Override
    public void urlsCreated(int count, boolean customAlias) {
    }

    @Override
    public void loginSucceeded() {
    }

    @Override
    public void loginFailed(String reason) {
    }

    @Override
    public void registrationSucceeded() {
    }

    @Override
    public void rateLimitExceeded(String dimension) {
    }

    @Override
    public void rateLimitBackendUnavailable(String dimension) {
    }

    @Override
    public void analyticsFlush(String kind, int records) {
    }

    @Override
    public void analyticsFlushFailed() {
    }
}

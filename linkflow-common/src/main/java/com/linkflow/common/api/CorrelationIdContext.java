package com.linkflow.common.api;

import org.slf4j.MDC;

/**
 * Thread-local holder for correlation IDs using SLF4J MDC.
 */
public final class CorrelationIdContext {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdContext() {
    }

    public static void set(String correlationId) {
        MDC.put(MDC_KEY, correlationId);
    }

    public static String getId() {
        return MDC.get(MDC_KEY);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}

package com.linkflow.observability.metrics;

import com.linkflow.common.event.EmailDeliveryEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns {@link EmailDeliveryEvent} into a Prometheus counter.
 * <p>
 * Lives here rather than in linkflow-notification so the mail transport stays free of metrics
 * concerns — the event was defined in linkflow-common for exactly this split.
 */
@Component
@RequiredArgsConstructor
public class EmailDeliveryMetricsListener {

    private final MeterRegistry registry;

    @EventListener
    public void onEmailDelivery(EmailDeliveryEvent event) {
        Counter.builder("linkflow.email.delivery")
                .description("Transactional email delivery outcomes")
                .tag("type", event.type().name().toLowerCase())
                .tag("outcome", event.outcome().name().toLowerCase())
                .register(registry)
                .increment();
    }
}

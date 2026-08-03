package com.linkflow.observability.metrics;

import com.linkflow.common.event.EmailDeliveryEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailDeliveryMetricsListenerTest {

    private SimpleMeterRegistry registry;
    private EmailDeliveryMetricsListener listener;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        listener = new EmailDeliveryMetricsListener(registry);
    }

    @Test
    void metersDeliveryByTypeAndOutcome() {
        listener.onEmailDelivery(new EmailDeliveryEvent(
                EmailDeliveryEvent.Type.EMAIL_VERIFICATION, EmailDeliveryEvent.Outcome.SENT));
        listener.onEmailDelivery(new EmailDeliveryEvent(
                EmailDeliveryEvent.Type.PASSWORD_RESET, EmailDeliveryEvent.Outcome.FAILED));
        listener.onEmailDelivery(new EmailDeliveryEvent(
                EmailDeliveryEvent.Type.EMAIL_CHANGE, EmailDeliveryEvent.Outcome.SKIPPED));

        assertEquals(1.0, counter("email_verification", "sent"));
        assertEquals(1.0, counter("password_reset", "failed"));
        assertEquals(1.0, counter("email_change", "skipped"));
    }

    private double counter(String type, String outcome) {
        return registry.get("linkflow.email.delivery")
                .tag("type", type)
                .tag("outcome", outcome)
                .counter()
                .count();
    }
}

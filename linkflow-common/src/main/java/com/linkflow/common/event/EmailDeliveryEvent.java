package com.linkflow.common.event;

/**
 * Published after each transactional email delivery attempt reaches a terminal outcome.
 * <p>
 * Lives in linkflow-common so linkflow-observability can meter delivery without depending on
 * linkflow-notification, keeping the metrics concern decoupled from the mail transport.
 */
public record EmailDeliveryEvent(Type type, Outcome outcome) {

    public enum Type {
        EMAIL_VERIFICATION,
        PASSWORD_RESET,
        EMAIL_CHANGE
    }

    public enum Outcome {
        /** Handed off to the SMTP relay without error. */
        SENT,
        /** All delivery attempts failed. */
        FAILED,
        /** Delivery is disabled by configuration; the message was logged and discarded. */
        SKIPPED
    }
}

package com.linkflow.analytics.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Tuning for the click-event stream's housekeeping.
 * <p>
 * These govern how quickly work abandoned by a dead instance is picked up again and how much
 * Redis memory the buffer may consume, both of which depend on deployment shape — flush interval,
 * replica count, restart frequency — rather than on anything intrinsic to the code.
 */
@Component
@ConfigurationProperties(prefix = "linkflow.analytics.stream")
@Getter
@Setter
public class AnalyticsStreamProperties {

    /**
     * How long an entry may stay unacknowledged before another instance takes it over.
     * <p>
     * Must comfortably exceed the flush interval, otherwise instances will steal entries from each
     * other mid-flush and do the same work twice.
     */
    private Duration reclaimPendingAfter = Duration.ofMinutes(2);

    /**
     * How long a consumer must be idle before it is removed from the group.
     * <p>
     * Consumers are named per instance, so every restart and every scaled-down replica leaves one
     * behind. Only consumers with nothing pending are removed, so this never discards work.
     */
    private Duration removeConsumerAfterIdle = Duration.ofHours(1);

    /**
     * Upper bound on stream length.
     * <p>
     * Processed entries are deleted as they are flushed, so this only comes into play when
     * flushing has been broken long enough for a backlog to accumulate. It caps Redis memory at
     * the cost of dropping the oldest unflushed clicks, which is preferable to Redis filling up
     * and taking down sessions and rate limiting with it.
     */
    private long maxLength = 500_000;

    /** Entries read from the stream per flush cycle. */
    private int batchSize = 1000;

    /**
     * How many times an entry may be delivered before it is discarded.
     * <p>
     * Some entries can never be persisted — a click buffered for a URL that was hard-deleted before
     * the flush will fail its foreign key on every attempt. Since unacknowledged entries are now
     * reclaimed and retried, such an entry would otherwise be retried on every cycle forever,
     * filling the log and never draining. Giving up after a few attempts keeps a single poison
     * entry from blocking the pipeline.
     */
    private int maxDeliveryAttempts = 5;
}

package com.linkflow.common.port;

import java.util.UUID;

/**
 * Port interface for click tracking. Implemented by linkflow-analytics module.
 * Consumed by linkflow-url module to record redirect clicks.
 */
public interface ClickTrackingPort {

    /**
     * Record a click event. Implementation should be async and best-effort.
     */
    void trackClick(ClickTrackingCommand command);

    /**
     * Command to record a click event.
     */
    record ClickTrackingCommand(
            UUID shortUrlId,
            String ipAddress,
            String userAgent,
            String referer
    ) {}
}

package com.linkflow.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when rate limiting cannot be enforced because Redis is unavailable.
 */
public class RateLimitBackendUnavailableException extends BaseException {

    public RateLimitBackendUnavailableException() {
        super(
                "Rate limiting is temporarily unavailable. Please try again later.",
                "RATE_LIMIT_BACKEND_UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }
}

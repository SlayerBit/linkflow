package com.linkflow.common.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BaseException {
    public RateLimitExceededException(String message) {
        super(message, "RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS);
    }
}

package com.linkflow.common.exception;

import org.springframework.http.HttpStatus;

public class AuthenticationException extends BaseException {
    public AuthenticationException(String message) {
        super(message, "AUTHENTICATION_FAILED", HttpStatus.UNAUTHORIZED);
    }
}

package com.linkflow.auth.domain.exception;

import com.linkflow.common.exception.AuthenticationException;

public class InvalidCredentialsException extends AuthenticationException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}

package com.linkflow.auth.domain.exception;

import com.linkflow.common.exception.AuthenticationException;

public class TokenRevokedException extends AuthenticationException {
    public TokenRevokedException() {
        super("Token has been revoked");
    }
}

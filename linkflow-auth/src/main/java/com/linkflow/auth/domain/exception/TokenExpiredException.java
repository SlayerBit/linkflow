package com.linkflow.auth.domain.exception;

import com.linkflow.common.exception.AuthenticationException;

public class TokenExpiredException extends AuthenticationException {
    public TokenExpiredException() {
        super("Token has expired");
    }
}

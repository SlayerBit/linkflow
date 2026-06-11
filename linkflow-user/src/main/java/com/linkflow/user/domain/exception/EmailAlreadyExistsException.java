package com.linkflow.user.domain.exception;

import com.linkflow.common.exception.ConflictException;

public class EmailAlreadyExistsException extends ConflictException {
    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email);
    }
}

package com.linkflow.user.domain.exception;

import com.linkflow.common.exception.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException(String identifier) {
        super("User", identifier);
    }
}

package com.linkflow.url.domain.exception;

import com.linkflow.common.exception.ConflictException;

public class AliasCollisionException extends ConflictException {

    public AliasCollisionException(String alias) {
        super("Short code alias already exists: " + alias);
    }
}

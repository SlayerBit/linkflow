package com.linkflow.url.domain.exception;

import com.linkflow.common.exception.GoneException;

public class UrlExpiredException extends GoneException {

    public UrlExpiredException(String shortCode) {
        super("Short URL has expired: " + shortCode);
    }
}

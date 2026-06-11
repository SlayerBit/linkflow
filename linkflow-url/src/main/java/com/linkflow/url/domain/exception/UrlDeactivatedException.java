package com.linkflow.url.domain.exception;

import com.linkflow.common.exception.GoneException;

public class UrlDeactivatedException extends GoneException {

    public UrlDeactivatedException(String shortCode) {
        super("Short URL is no longer available: " + shortCode);
    }
}

package com.linkflow.common.exception;

import org.springframework.http.HttpStatus;

public class GoneException extends BaseException {
    public GoneException(String message) {
        super(message, "GONE", HttpStatus.GONE);
    }
}

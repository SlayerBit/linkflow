package com.linkflow.url.domain.exception;

import com.linkflow.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class InvalidUrlException extends BaseException {

    public InvalidUrlException(String message) {
        super(message, "INVALID_URL", HttpStatus.BAD_REQUEST);
    }
}

package com.linkflow.auth.domain.exception;

import com.linkflow.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends BaseException {
    public EmailNotVerifiedException() {
        super("Email address not verified. Please check your inbox.", "EMAIL_NOT_VERIFIED", HttpStatus.UNAUTHORIZED);
    }
}

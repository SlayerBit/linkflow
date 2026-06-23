package com.linkflow.user.domain.exception;

import com.linkflow.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class AdminSelfActionException extends BaseException {
    public AdminSelfActionException(String message) {
        super(message, "ADMIN_SELF_ACTION_FORBIDDEN", HttpStatus.BAD_REQUEST);
    }
}

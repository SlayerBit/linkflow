package com.linkflow.user.domain.exception;

import com.linkflow.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class LastAdminException extends BaseException {
    public LastAdminException() {
        super("Cannot remove the last active admin account", "LAST_ADMIN", HttpStatus.BAD_REQUEST);
    }
}

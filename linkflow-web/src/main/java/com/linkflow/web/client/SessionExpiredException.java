package com.linkflow.web.client;

public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException() {
        super("Session expired");
    }
}

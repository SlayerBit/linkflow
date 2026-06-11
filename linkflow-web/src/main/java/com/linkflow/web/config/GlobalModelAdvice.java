package com.linkflow.web.config;

import com.linkflow.web.session.AuthState;
import com.linkflow.web.session.SessionKeys;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpSession;

@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("auth")
    public AuthState auth(HttpSession session) {
        Object value = session.getAttribute(SessionKeys.AUTH_STATE);
        return value instanceof AuthState authState ? authState : null;
    }
}

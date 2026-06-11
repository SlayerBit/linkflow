package com.linkflow.web.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.linkflow.web.client.BackendApiException;
import com.linkflow.web.client.SessionExpiredException;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SessionExpiredException.class)
    public String handleSessionExpired() {
        return "redirect:/login?expired=true";
    }

    @ExceptionHandler(BackendApiException.class)
    public String handleBackendApiException(BackendApiException ex, HttpServletRequest request) {
        String target = safeRedirectTarget(request.getHeader("Referer"));
        String separator = target.contains("?") ? "&" : "?";
        return "redirect:" + target + separator + "error=" + urlEncode(ex.getMessage());
    }

    private String safeRedirectTarget(String referer) {
        if (referer == null || referer.isBlank()) {
            return "/dashboard";
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getPath() == null || uri.getPath().isBlank()) {
                return "/dashboard";
            }
            String path = uri.getPath();
            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                path = path + "?" + uri.getQuery();
            }
            return path.startsWith("/") ? path : "/dashboard";
        } catch (IllegalArgumentException ex) {
            return "/dashboard";
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

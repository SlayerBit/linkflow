package com.linkflow.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkflow.common.api.ApiErrorResponse;
import com.linkflow.common.api.CorrelationIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                "ACCESS_DENIED",
                "You do not have permission to access this resource",
                CorrelationIdContext.getId()
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}

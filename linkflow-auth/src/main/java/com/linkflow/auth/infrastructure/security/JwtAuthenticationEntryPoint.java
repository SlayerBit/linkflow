package com.linkflow.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkflow.common.api.ApiErrorResponse;
import com.linkflow.common.api.CorrelationIdContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiErrorResponse errorResponse = ApiErrorResponse.of(
                "AUTHENTICATION_REQUIRED",
                "Authentication is required to access this resource",
                CorrelationIdContext.getId()
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}

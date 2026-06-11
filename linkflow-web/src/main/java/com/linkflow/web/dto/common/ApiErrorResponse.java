package com.linkflow.web.dto.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiErrorResponse(
        boolean success,
        Instant timestamp,
        String correlationId,
        String errorCode,
        String message,
        List<String> details
) {
}

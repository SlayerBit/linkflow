package com.linkflow.web.dto.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiResponse<T>(
        boolean success,
        Instant timestamp,
        String correlationId,
        T data
) {
}

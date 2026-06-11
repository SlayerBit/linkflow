package com.linkflow.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error response wrapper.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    @Builder.Default
    private final boolean success = false;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    private final String correlationId;
    private final String errorCode;
    private final String message;
    private final List<String> details;

    public static ApiErrorResponse of(String errorCode, String message, String correlationId) {
        return ApiErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .correlationId(correlationId)
                .build();
    }

    public static ApiErrorResponse of(String errorCode, String message, List<String> details, String correlationId) {
        return ApiErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .correlationId(correlationId)
                .build();
    }
}

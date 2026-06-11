package com.linkflow.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Standard API response wrapper for all successful responses.
 */
@Getter
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @Builder.Default
    private final boolean success = true;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    private final String correlationId;

    private final T data;

    public static <T> ApiResponse<T> of(T data, String correlationId) {
        return ApiResponse.<T>builder()
                .data(data)
                .correlationId(correlationId)
                .build();
    }

    public static <T> ApiResponse<T> of(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .correlationId(CorrelationIdContext.getId())
                .build();
    }

    public static ApiResponse<Void> empty() {
        return ApiResponse.<Void>builder()
                .correlationId(CorrelationIdContext.getId())
                .build();
    }
}

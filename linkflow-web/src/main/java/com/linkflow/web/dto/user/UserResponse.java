package com.linkflow.web.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
}

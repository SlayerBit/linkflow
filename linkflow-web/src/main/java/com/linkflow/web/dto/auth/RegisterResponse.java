package com.linkflow.web.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RegisterResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Set<String> roles,
        Instant createdAt,
        String verificationToken
) {
}

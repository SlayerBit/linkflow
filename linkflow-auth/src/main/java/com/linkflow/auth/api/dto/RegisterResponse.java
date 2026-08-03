package com.linkflow.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "Newly created account. Activation requires the link sent by email.")
public class RegisterResponse {

    @Schema(example = "3f1a9c52-4f1e-4c31-9a1e-2b7d5f9c8a10")
    private final UUID id;

    @Schema(example = "ada@example.com")
    private final String email;

    private final String firstName;

    private final String lastName;

    @Schema(example = "[\"ROLE_USER\"]")
    private final Set<String> roles;

    private final Instant createdAt;
}

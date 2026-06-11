package com.linkflow.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class RegisterResponse {
    private final UUID id;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final Set<String> roles;
    private final Instant createdAt;
}

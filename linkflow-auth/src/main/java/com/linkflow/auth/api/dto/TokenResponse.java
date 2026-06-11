package com.linkflow.auth.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TokenResponse {
    private final String accessToken;
    private final String refreshToken;
    @Builder.Default
    private final String tokenType = "Bearer";
    private final long expiresIn;
}

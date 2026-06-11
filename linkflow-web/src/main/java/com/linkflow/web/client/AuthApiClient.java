package com.linkflow.web.client;

import com.linkflow.web.dto.auth.RegisterResponse;
import com.linkflow.web.dto.auth.TokenResponse;
import com.linkflow.web.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuthApiClient {

    private final BackendClient backendClient;

    public TokenResponse login(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        var response = backendClient.exchangeForBody(
                backendClient.postPublic("/api/v1/auth/login").body(body),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() {}
        );
        return response.data();
    }

    public RegisterResponse register(String email, String password, String firstName, String lastName) {
        Map<String, String> body = Map.of(
                "email", email,
                "password", password,
                "firstName", firstName,
                "lastName", lastName != null ? lastName : ""
        );
        var response = backendClient.exchangeForBody(
                backendClient.postPublic("/api/v1/auth/register").body(body),
                new ParameterizedTypeReference<ApiResponse<RegisterResponse>>() {}
        );
        return response.data();
    }

    public TokenResponse refresh(String refreshToken) {
        Map<String, String> body = Map.of("refreshToken", refreshToken);
        var response = backendClient.exchangeForBody(
                backendClient.postPublic("/api/v1/auth/refresh").body(body),
                new ParameterizedTypeReference<ApiResponse<TokenResponse>>() {}
        );
        return response.data();
    }

    public void logout(String accessToken, String refreshToken) {
        Map<String, String> body = Map.of("refreshToken", refreshToken);
        backendClient.exchangeForBody(
                backendClient.post("/api/v1/auth/logout", accessToken).body(body),
                new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
        );
    }
}

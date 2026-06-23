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

    public void verifyEmail(String token) {
        Map<String, String> body = Map.of("token", token);
        backendClient.exchangeForBody(
                backendClient.postPublic("/api/v1/auth/verify-email").body(body),
                new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
        );
    }

    public String forgotPassword(String email) {
        Map<String, String> body = Map.of("email", email);
        var response = backendClient.exchangeForBody(
                backendClient.postPublic("/api/v1/auth/forgot-password").body(body),
                new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
        );
        return response.data().get("token");
    }

    public void resetPassword(String token, String newPassword) {
        Map<String, String> body = Map.of("token", token, "newPassword", newPassword);
        backendClient.exchangeForBody(
                backendClient.postPublic("/api/v1/auth/reset-password").body(body),
                new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
        );
    }

    public void changePassword(String accessToken, String currentPassword, String newPassword) {
        Map<String, String> body = Map.of("currentPassword", currentPassword, "newPassword", newPassword);
        backendClient.exchangeForBody(
                backendClient.post("/api/v1/auth/change-password", accessToken).body(body),
                new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {}
        );
    }
}

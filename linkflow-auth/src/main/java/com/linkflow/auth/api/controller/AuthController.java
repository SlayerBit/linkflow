package com.linkflow.auth.api.controller;

import com.linkflow.auth.api.dto.*;
import com.linkflow.auth.application.service.AuthService;
import com.linkflow.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token refresh, logout")
public class AuthController {

    private final AuthService authService;
    private final com.linkflow.auth.infrastructure.config.LinkflowSecurityProperties securityProperties;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        if (!securityProperties.isExposeDevTokens()) {
            response = RegisterResponse.builder()
                    .id(response.getId())
                    .email(response.getEmail())
                    .firstName(response.getFirstName())
                    .lastName(response.getLastName())
                    .roles(response.getRoles())
                    .createdAt(response.getCreatedAt())
                    .verificationToken(null)
                    .build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive tokens")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and revoke refresh token")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
            @Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Logged out successfully")));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password for the authenticated user")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Password changed successfully")));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify user email using a registration token")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getToken());
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Email verified successfully")));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset token")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        var token = authService.requestPasswordReset(request.getEmail());
        if (securityProperties.isExposeDevTokens() && token.isPresent()) {
            return ResponseEntity.ok(ApiResponse.of(Map.of(
                    "message", "If an account exists for that email, a reset link has been sent.",
                    "token", token.get()
            )));
        }
        return ResponseEntity.ok(ApiResponse.of(Map.of(
                "message", "If an account exists for that email, a reset link has been sent."
        )));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using token")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Password reset successfully")));
    }
}

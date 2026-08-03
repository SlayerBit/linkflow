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
@Tag(name = "Authentication", description = "Registration, login, token lifecycle, and account recovery")
public class AuthController {

    /**
     * Returned verbatim for both known and unknown addresses. The wording is deliberately
     * non-committal so the response cannot be used to discover which addresses are registered.
     */
    private static final String RECOVERY_ACK =
            "If an account exists for that email, a message with next steps has been sent.";

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user",
            description = "Creates the account and emails an activation link. The account cannot sign in "
                    + "until the link is opened, unless email verification is disabled for the environment. "
                    + "Responds 409 if the address is already registered.")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login and receive tokens",
            description = "Returns a short-lived access token and a rotating refresh token. "
                    + "Responds 401 for invalid credentials, and 401 with error code "
                    + "`EMAIL_NOT_VERIFIED` when the account exists but has not been activated — "
                    + "the distinct code is what lets a client offer to resend the link rather "
                    + "than insisting the password was wrong.")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.of(authService.login(request)));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Rotates the refresh token: the presented token is revoked and a replacement "
                    + "is issued, so each refresh token is usable exactly once.")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.of(authService.refresh(request)));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout and revoke tokens",
            description = "Revokes the refresh token and invalidates access tokens issued before now.")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(
            @Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Logged out successfully")));
    }

    @PostMapping("/change-password")
    @Operation(
            summary = "Change password for the authenticated user",
            description = "Requires the current password. All existing sessions are revoked on success.")
    public ResponseEntity<ApiResponse<Map<String, String>>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Password changed successfully")));
    }

    @PostMapping("/verify-email")
    @Operation(
            summary = "Activate an account using the emailed token",
            description = """
                    Tokens are single-use and expire 24 hours after being issued. Issuing a new one \
                    invalidates any earlier link.

                    Idempotent: once the account is verified, presenting the link again responds 200 \
                    rather than reporting the token as spent. Mail scanners and link prefetchers open \
                    these links before the recipient does, so a strict reading would show an error to \
                    a user whose account is in exactly the state they wanted.

                    Responds 404 if the token was never issued, and 409 if it expired or was \
                    invalidated before the account was activated.""")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.getToken());
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Email verified successfully")));
    }

    @PostMapping("/resend-verification")
    @Operation(
            summary = "Request a new activation link",
            description = """
                    Issues a fresh link, valid for 24 hours, and invalidates any earlier one.

                    Always responds 200 — whether the address is registered, already verified, or \
                    inside its send cooldown. Distinguishing those would turn an unauthenticated \
                    endpoint into an account-existence oracle.

                    Repeat requests for the same address are throttled, so a caller cannot use this \
                    to flood somebody else's inbox. A suppressed request leaves the previous link \
                    working.""")
    public ResponseEntity<ApiResponse<Map<String, String>>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", RECOVERY_ACK)));
    }

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Start password recovery",
            description = """
                    Emails a reset link valid for 15 minutes and invalidates any earlier one.

                    Always responds 200, whether or not the address is registered and whether or not \
                    the request was throttled, so the response cannot be used to enumerate accounts. \
                    Repeat requests for the same address are subject to the same per-recipient \
                    cooldown as resend-verification.""")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", RECOVERY_ACK)));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Complete password recovery",
            description = """
                    Consumes the emailed token, sets the new password, and revokes every existing \
                    session so a password changed under duress also ends the intruder's access.

                    Strictly single-use, unlike verify-email: this token authorises setting a secret, \
                    so honouring it twice would let a replayed link overwrite a password that had \
                    since been changed. Responds 404 for an unknown token and 409 for one that is \
                    spent, expired, or superseded.""")
    public ResponseEntity<ApiResponse<Map<String, String>>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.of(Map.of("message", "Password reset successfully")));
    }
}

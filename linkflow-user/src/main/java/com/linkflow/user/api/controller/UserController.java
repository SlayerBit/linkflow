package com.linkflow.user.api.controller;

import com.linkflow.common.api.ApiResponse;
import com.linkflow.user.api.dto.UpdateProfileRequest;
import com.linkflow.user.api.dto.UserResponse;
import com.linkflow.user.api.dto.EmailChangeRequestDto;
import com.linkflow.user.api.dto.EmailChangeVerifyDto;
import com.linkflow.user.application.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile management")
public class UserController {

    private final UserService userService;

    @Value("${linkflow.security.expose-dev-tokens:false}")
    private boolean exposeDevTokens;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UserResponse user = userService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUser(
            @Valid @RequestBody UpdateProfileRequest request) {
        UserResponse user = userService.updateCurrentUser(request);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    @PostMapping("/me/email-change-request")
    @Operation(summary = "Request email change verification token")
    public ResponseEntity<ApiResponse<Map<String, String>>> requestEmailChange(
            @Valid @RequestBody EmailChangeRequestDto request) {
        String token = userService.requestEmailChange(request.getCurrentPassword(), request.getNewEmail());
        if (exposeDevTokens) {
            return ResponseEntity.ok(ApiResponse.of(Map.of(
                    "token", token,
                    "message", "Email change verification token generated."
            )));
        }
        return ResponseEntity.ok(ApiResponse.of(Map.of(
                "message", "If the request is valid, a verification link has been sent to the new email address."
        )));
    }

    @PostMapping("/verify-email-change")
    @Operation(summary = "Verify and complete email change")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyEmailChange(
            @Valid @RequestBody EmailChangeVerifyDto request) {
        userService.verifyEmailChange(request.getToken());
        return ResponseEntity.ok(ApiResponse.of(Map.of(
                "message", "Email updated successfully. Please log back in."
        )));
    }
}

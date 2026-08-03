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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User profile management")
public class UserController {

    private final UserService userService;

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
    @Operation(
            summary = "Request an email change",
            description = """
                    Sends a confirmation link, valid for 24 hours, to the new address. The current \
                    address stays active and usable for sign-in until that link is opened, so a typo \
                    cannot strand the account at a mailbox nobody owns.

                    Requires the current password even though the caller is already authenticated: a \
                    stolen session would otherwise be enough to redirect the account to an attacker's \
                    mailbox, turning temporary access into permanent ownership.

                    Responds 409 if the password is wrong, the address is unchanged, or it already \
                    belongs to another account; 429 if a confirmation was sent to that address a \
                    moment ago.""")
    public ResponseEntity<ApiResponse<Map<String, String>>> requestEmailChange(
            @Valid @RequestBody EmailChangeRequestDto request) {
        userService.requestEmailChange(request.getCurrentPassword(), request.getNewEmail());
        return ResponseEntity.ok(ApiResponse.of(Map.of(
                "message", "Check the new address for a confirmation link to complete the change."
        )));
    }

    @PostMapping("/verify-email-change")
    @Operation(
            summary = "Confirm and complete an email change",
            description = """
                    Consumes the emailed token, moves the account to the new address, and revokes \
                    every session — the sign-in identity has changed, so credentials minted against \
                    the old one must not survive.

                    Idempotent once the account already carries the new address, because mail \
                    scanners open these links before the recipient does. Responds 404 for an unknown \
                    token, and 409 if the link was superseded, expired, or the address was claimed by \
                    another account while the link sat unopened.""")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyEmailChange(
            @Valid @RequestBody EmailChangeVerifyDto request) {
        userService.verifyEmailChange(request.getToken());
        return ResponseEntity.ok(ApiResponse.of(Map.of(
                "message", "Email updated successfully. Please log back in."
        )));
    }
}

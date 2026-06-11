package com.linkflow.user.api.controller;

import com.linkflow.common.api.ApiResponse;
import com.linkflow.common.api.PagedResponse;
import com.linkflow.user.api.dto.UserResponse;
import com.linkflow.user.application.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Users", description = "Admin user management")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List all users (admin)")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        size = Math.min(size, 100);
        Sort sort = "asc".equalsIgnoreCase(direction)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<UserResponse> users = userService.listUsers(pageable);
        PagedResponse<UserResponse> pagedResponse = PagedResponse.of(
                users.getContent(), users.getNumber(), users.getSize(),
                users.getTotalElements(), users.getTotalPages()
        );
        return ResponseEntity.ok(ApiResponse.of(pagedResponse));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID (admin)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    @PatchMapping("/{id}/disable")
    @Operation(summary = "Disable a user account (admin)")
    public ResponseEntity<ApiResponse<UserResponse>> disableUser(@PathVariable UUID id) {
        UserResponse user = userService.disableUser(id);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    @PatchMapping("/{id}/enable")
    @Operation(summary = "Re-enable a disabled user account (admin)")
    public ResponseEntity<ApiResponse<UserResponse>> enableUser(@PathVariable UUID id) {
        UserResponse user = userService.enableUser(id);
        return ResponseEntity.ok(ApiResponse.of(user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a user account (admin)")
    public ResponseEntity<ApiResponse<UserResponse>> softDeleteUser(@PathVariable UUID id) {
        UserResponse user = userService.softDeleteUser(id);
        return ResponseEntity.ok(ApiResponse.of(user));
    }
}

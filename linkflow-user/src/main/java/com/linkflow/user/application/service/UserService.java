package com.linkflow.user.application.service;

import com.linkflow.common.exception.ConflictException;
import com.linkflow.common.port.TokenRevocationPort;
import com.linkflow.common.port.UserLookupPort;
import com.linkflow.common.security.SecurityConstants;
import com.linkflow.common.security.UserPrincipal;
import com.linkflow.user.api.dto.UpdateProfileRequest;
import com.linkflow.user.api.dto.UserResponse;
import com.linkflow.user.domain.entity.User;
import com.linkflow.user.domain.entity.EmailChangeRequest;
import com.linkflow.user.domain.exception.AdminSelfActionException;
import com.linkflow.user.domain.exception.LastAdminException;
import com.linkflow.user.domain.exception.UserNotFoundException;
import com.linkflow.user.domain.repository.UserRepository;
import com.linkflow.user.domain.repository.EmailChangeRequestRepository;
import com.linkflow.user.infrastructure.adapter.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final TokenRevocationPort tokenRevocationPort;
    private final EmailChangeRequestRepository emailChangeRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        UserPrincipal principal = getCurrentPrincipal();
        User user = userRepository.findByIdAndNotDeleted(principal.getId())
                .orElseThrow(() -> new UserNotFoundException(principal.getId().toString()));
        return toResponse(user, principal.getRoles());
    }

    @Transactional
    public UserResponse updateCurrentUser(UpdateProfileRequest request) {
        UserPrincipal principal = getCurrentPrincipal();
        User user = userRepository.findByIdAndNotDeleted(principal.getId())
                .orElseThrow(() -> new UserNotFoundException(principal.getId().toString()));

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user = userRepository.save(user);
        log.info("User profile updated: userId={}", user.getId());
        return toResponse(user, principal.getRoles());
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAllActive(pageable)
                .map(user -> toResponse(user, Set.of()));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new UserNotFoundException(id.toString()));
        return toResponse(user, Set.of());
    }

    @Transactional
    public UserResponse disableUser(UUID id) {
        assertAdminActionAllowed(id);
        User user = userRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new UserNotFoundException(id.toString()));
        user.setEnabled(false);
        user = userRepository.save(user);
        revokeUserSessions(id);
        log.info("User disabled: userId={}", id);
        return toResponse(user, Set.of());
    }

    @Transactional
    public UserResponse enableUser(UUID id) {
        User user = userRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new UserNotFoundException(id.toString()));
        user.setEnabled(true);
        user = userRepository.save(user);
        log.info("User enabled: userId={}", id);
        return toResponse(user, Set.of());
    }

    @Transactional
    public UserResponse softDeleteUser(UUID id) {
        assertAdminActionAllowed(id);
        User user = userRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new UserNotFoundException(id.toString()));
        user.softDelete();
        user = userRepository.save(user);
        revokeUserSessions(id);
        log.info("User soft-deleted: userId={}", id);
        return toResponse(user, Set.of());
    }

    private void assertAdminActionAllowed(UUID targetUserId) {
        UserPrincipal currentAdmin = getCurrentPrincipal();
        if (currentAdmin.getId().equals(targetUserId)) {
            throw new AdminSelfActionException("Admins cannot disable or delete their own account");
        }

        User target = userRepository.findByIdAndNotDeleted(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId.toString()));
        Set<String> targetRoles = roleService.resolveRoleNames(target.getRoleIds());
        if (targetRoles.contains(SecurityConstants.ROLE_ADMIN) && userRepository.countActiveAdmins() <= 1) {
            throw new LastAdminException();
        }
    }

    private void revokeUserSessions(UUID userId) {
        Instant now = Instant.now();
        tokenRevocationPort.revokeAllRefreshTokensForUser(userId);
        tokenRevocationPort.markAccessTokensRevokedAfter(userId, now);
    }

    private UserPrincipal getCurrentPrincipal() {
        return (UserPrincipal) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }

    private UserResponse toResponse(User user, Set<String> roles) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(roles.isEmpty() ? roleService.resolveRoleNames(user.getRoleIds()) : roles)
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    @Transactional
    public String requestEmailChange(String currentPassword, String newEmail) {
        UserPrincipal principal = getCurrentPrincipal();
        User user = userRepository.findByIdAndNotDeleted(principal.getId())
                .orElseThrow(() -> new UserNotFoundException(principal.getId().toString()));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ConflictException("Incorrect current password");
        }

        if (user.getEmail().equalsIgnoreCase(newEmail)) {
            throw new ConflictException("New email must be different from current email");
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new ConflictException("Email is already taken");
        }

        String rawToken = generateOpaqueToken();
        String tokenHash = hashToken(rawToken);

        EmailChangeRequest request = EmailChangeRequest.builder()
                .userId(user.getId())
                .newEmail(newEmail.toLowerCase())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(java.time.Duration.ofHours(24)))
                .build();

        emailChangeRequestRepository.save(request);
        log.info("Email change request generated for userId={}, newEmail={}", user.getId(), newEmail);
        return rawToken;
    }

    @Transactional
    public void verifyEmailChange(String rawToken) {
        String hash = hashToken(rawToken);
        EmailChangeRequest request = emailChangeRequestRepository.findByTokenHash(hash)
                .orElseThrow(() -> new com.linkflow.common.exception.ResourceNotFoundException("Email change request", "not found"));

        if (request.isUsed()) {
            throw new ConflictException("Email change request token has already been used");
        }

        if (request.isExpired()) {
            throw new ConflictException("Email change request token has expired");
        }

        User user = userRepository.findByIdAndNotDeleted(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException(request.getUserId().toString()));

        if (userRepository.existsByEmail(request.getNewEmail())) {
            throw new ConflictException("Email is already taken");
        }

        user.setEmail(request.getNewEmail());
        user.setEmailVerified(true);
        userRepository.save(user);

        request.setUsed(true);
        emailChangeRequestRepository.save(request);

        revokeUserSessions(user.getId());
        log.info("Email updated successfully for userId={} to {}", user.getId(), request.getNewEmail());
    }

    @Transactional
    public UserResponse updateUserRoles(UUID targetUserId, Set<String> roles) {
        UserPrincipal currentAdmin = getCurrentPrincipal();
        boolean removingAdmin = false;

        User target = userRepository.findByIdAndNotDeleted(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId.toString()));
        Set<String> currentRoles = roleService.resolveRoleNames(target.getRoleIds());

        Set<String> normalizedRoles = roles.stream()
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .collect(java.util.stream.Collectors.toSet());

        if (currentRoles.contains("ADMIN") && !normalizedRoles.contains("ADMIN")) {
            removingAdmin = true;
        }

        if (currentAdmin.getId().equals(targetUserId) && removingAdmin) {
            throw new AdminSelfActionException("Admins cannot demote their own account");
        }

        if (removingAdmin && userRepository.countActiveAdmins() <= 1) {
            throw new LastAdminException();
        }

        Set<Long> roleIds = roleService.resolveRoleIds(normalizedRoles);
        if (roleIds.isEmpty()) {
            throw new ConflictException("User must have at least one role");
        }
        target.setRoleIds(roleIds);
        target = userRepository.save(target);

        revokeUserSessions(targetUserId);

        log.info("User roles updated: userId={}, roles={}", targetUserId, normalizedRoles);
        return toResponse(target, normalizedRoles);
    }
}


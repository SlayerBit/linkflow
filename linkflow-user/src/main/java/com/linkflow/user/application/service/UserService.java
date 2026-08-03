package com.linkflow.user.application.service;

import com.linkflow.common.event.EmailRequestedEvent;
import com.linkflow.common.exception.ConflictException;
import com.linkflow.common.exception.RateLimitExceededException;
import com.linkflow.common.exception.ResourceNotFoundException;
import com.linkflow.common.mail.MailSendCooldown;
import com.linkflow.common.port.TokenRevocationPort;
import com.linkflow.common.port.UserLookupPort;
import com.linkflow.common.security.SecureTokenGenerator;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    static final Duration EMAIL_CHANGE_TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final TokenRevocationPort tokenRevocationPort;
    private final EmailChangeRequestRepository emailChangeRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator tokenGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final MailSendCooldown mailSendCooldown;

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

    /**
     * Starts an email change. The confirmation link goes to the <em>new</em> address, so control
     * of that mailbox is proven before the account moves; the current address stays active and
     * usable for sign-in until then.
     * <p>
     * Re-entering the current password is required because an attacker holding a live session
     * could otherwise redirect the account to a mailbox they own.
     */
    @Transactional
    public void requestEmailChange(String currentPassword, String newEmail) {
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

        String normalizedEmail = newEmail.toLowerCase();

        // Reported rather than silently swallowed, unlike the unauthenticated recovery flows: the
        // caller is signed in and nominated this address themselves, so there is no account to
        // enumerate and nothing gained by pretending a suppressed request succeeded.
        //
        // Checked before the existing request is invalidated, so being refused here leaves any
        // outstanding confirmation link working.
        if (!mailSendCooldown.tryAcquire(MailSendCooldown.Purpose.EMAIL_CHANGE, normalizedEmail)) {
            throw new RateLimitExceededException(
                    "A confirmation link was just sent to that address. Check the inbox, "
                            + "including spam, before requesting another.");
        }

        emailChangeRequestRepository.markAllUnusedAsUsedForUser(user.getId());

        String rawToken = tokenGenerator.generateToken();

        emailChangeRequestRepository.save(EmailChangeRequest.builder()
                .userId(user.getId())
                .newEmail(normalizedEmail)
                .tokenHash(tokenGenerator.hash(rawToken))
                .expiresAt(Instant.now().plus(EMAIL_CHANGE_TOKEN_TTL))
                .build());

        eventPublisher.publishEvent(new EmailRequestedEvent.EmailChangeRequested(
                normalizedEmail, user.getFirstName(), rawToken, EMAIL_CHANGE_TOKEN_TTL));

        log.info("Email change requested for userId={}", user.getId());
    }

    /**
     * Completes an email change from the link sent to the new address.
     * <p>
     * Idempotent for the same reason {@code AuthService.verifyEmail} is: mail scanners and link
     * prefetchers follow these links before the recipient does. If the account already carries the
     * requested address the work is done, and saying so is more useful than reporting that the link
     * was spent.
     */
    @Transactional
    public void verifyEmailChange(String rawToken) {
        EmailChangeRequest request = emailChangeRequestRepository
                .findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(() -> new ResourceNotFoundException("Confirmation link", "is not valid"));

        User user = userRepository.findByIdAndNotDeleted(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException(request.getUserId().toString()));

        if (user.getEmail().equalsIgnoreCase(request.getNewEmail())) {
            if (!request.isUsed()) {
                request.setUsed(true);
                emailChangeRequestRepository.save(request);
            }
            log.debug("Email change link presented for userId={}; address already applied", user.getId());
            return;
        }

        if (request.isUsed()) {
            throw new ConflictException("This confirmation link has already been used.");
        }

        if (request.isExpired()) {
            throw new ConflictException(
                    "This confirmation link has expired. Request the email change again to continue.");
        }

        // Checked at redemption rather than only at request time: the address may have been claimed
        // by someone else during the 24 hours the link was valid.
        if (userRepository.existsByEmail(request.getNewEmail())) {
            throw new ConflictException("Email is already taken");
        }

        user.setEmail(request.getNewEmail());
        user.setEmailVerified(true);
        userRepository.save(user);

        request.setUsed(true);
        emailChangeRequestRepository.save(request);

        revokeUserSessions(user.getId());
        log.info("Email updated for userId={}; all sessions revoked", user.getId());
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


package com.linkflow.user.application.service;

import com.linkflow.common.security.UserPrincipal;
import com.linkflow.user.api.dto.UpdateProfileRequest;
import com.linkflow.user.api.dto.UserResponse;
import com.linkflow.user.domain.entity.User;
import com.linkflow.user.domain.exception.UserNotFoundException;
import com.linkflow.user.domain.repository.UserRepository;
import com.linkflow.user.infrastructure.adapter.RoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;

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
        User user = userRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new UserNotFoundException(id.toString()));
        user.setEnabled(false);
        user = userRepository.save(user);
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
        User user = userRepository.findByIdAndNotDeleted(id)
                .orElseThrow(() -> new UserNotFoundException(id.toString()));
        user.softDelete();
        user = userRepository.save(user);
        log.info("User soft-deleted: userId={}", id);
        return toResponse(user, Set.of());
    }

    @Transactional(readOnly = true)
    public long countActiveUsers() {
        return userRepository.countActive();
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
}

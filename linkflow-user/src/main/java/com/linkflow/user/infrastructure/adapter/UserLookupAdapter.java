package com.linkflow.user.infrastructure.adapter;

import com.linkflow.common.port.UserLookupPort;
import com.linkflow.user.domain.entity.User;
import com.linkflow.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter implementing UserLookupPort so that linkflow-auth can
 * access user data without a direct dependency on linkflow-user.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserLookupAdapter implements UserLookupPort {

    private final UserRepository userRepository;
    private final RoleService roleService;

    @Override
    @Transactional(readOnly = true)
    public Optional<UserPrincipalData> findByEmail(String email) {
        return userRepository.findByEmailAndNotDeleted(email)
                .map(this::toData);
    }

    @Override
    @Transactional
    public UserPrincipalData createUser(CreateUserCommand command) {
        User user = User.builder()
                .email(command.email())
                .passwordHash(command.passwordHash())
                .firstName(command.firstName())
                .lastName(command.lastName())
                .enabled(true)
                .emailVerified(command.emailVerified())
                .deleted(false)
                .build();

        Set<Long> roleIds = roleService.resolveRoleIds(command.roles());
        user.setRoleIds(roleIds);

        user = userRepository.save(user);
        log.info("User created: userId={}, email={}", user.getId(), user.getEmail());
        return toData(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserPrincipalData> findById(UUID id) {
        return userRepository.findByIdAndNotDeleted(id)
                .map(this::toData);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveUsers() {
        return userRepository.countActive();
    }

    @Override
    @Transactional
    public void updatePasswordHash(UUID userId, String passwordHash) {
        User user = userRepository.findByIdAndNotDeleted(userId)
                .orElseThrow(() -> new com.linkflow.user.domain.exception.UserNotFoundException(userId.toString()));
        user.setPasswordHash(passwordHash);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updateEmailVerified(UUID userId, boolean emailVerified) {
        User user = userRepository.findByIdAndNotDeleted(userId)
                .orElseThrow(() -> new com.linkflow.user.domain.exception.UserNotFoundException(userId.toString()));
        user.setEmailVerified(emailVerified);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updateEmail(UUID userId, String newEmail) {
        User user = userRepository.findByIdAndNotDeleted(userId)
                .orElseThrow(() -> new com.linkflow.user.domain.exception.UserNotFoundException(userId.toString()));
        user.setEmail(newEmail);
        userRepository.save(user);
    }

    private UserPrincipalData toData(User user) {
        Set<String> roleNames = roleService.resolveRoleNames(user.getRoleIds());
        return new UserPrincipalData(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFirstName(),
                user.getLastName(),
                roleNames,
                user.isEnabled(),
                user.isEmailVerified()
        );
    }
}

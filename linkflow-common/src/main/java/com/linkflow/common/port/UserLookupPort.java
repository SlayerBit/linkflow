package com.linkflow.common.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port interface for user lookup operations. Implemented by linkflow-user module.
 * Consumed by linkflow-auth module to avoid direct module dependency.
 */
public interface UserLookupPort {

    Optional<UserPrincipalData> findByEmail(String email);

    UserPrincipalData createUser(CreateUserCommand command);

    boolean existsByEmail(String email);

    Optional<UserPrincipalData> findById(UUID id);

    long countActiveUsers();

    void updatePasswordHash(UUID userId, String passwordHash);

    void updateEmailVerified(UUID userId, boolean emailVerified);

    void updateEmail(UUID userId, String newEmail);

    /**
     * Immutable data carrier for user principal information.
     */
    record UserPrincipalData(
            UUID id,
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            Set<String> roles,
            boolean enabled,
            boolean emailVerified
    ) {}

    /**
     * Command to create a new user.
     */
    record CreateUserCommand(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            Set<String> roles,
            boolean emailVerified
    ) {}
}

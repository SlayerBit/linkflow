package com.linkflow.user.domain.repository;

import com.linkflow.user.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deleted = false")
    Optional<User> findByEmailAndNotDeleted(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deleted = false")
    Optional<User> findByIdAndNotDeleted(@Param("id") UUID id);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.deleted = false")
    Page<User> findAllActive(Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deleted = false")
    long countActive();

    @Query(value = """
            SELECT COUNT(DISTINCT u.id)
            FROM users u
            INNER JOIN user_roles ur ON ur.user_id = u.id
            INNER JOIN roles r ON r.id = ur.role_id
            WHERE u.deleted = false AND u.enabled = true AND r.name = 'ADMIN'
            """, nativeQuery = true)
    long countActiveAdmins();
}

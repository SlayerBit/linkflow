package com.linkflow.user.domain.repository;

import com.linkflow.user.domain.entity.EmailChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, UUID> {

    Optional<EmailChangeRequest> findByTokenHash(String tokenHash);

    /**
     * Retires outstanding change requests so a user cannot have two pending target addresses,
     * and an abandoned request cannot be confirmed later.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailChangeRequest r set r.used = true "
            + "where r.userId = :userId and r.used = false")
    int markAllUnusedAsUsedForUser(@Param("userId") UUID userId);

    /**
     * Removes requests that can no longer be confirmed and are older than the retention window,
     * bounding growth of this table while keeping recent history for support queries.
     */
    @Modifying
    @Query("delete from EmailChangeRequest r "
            + "where (r.used = true or r.expiresAt < :now) and r.createdAt < :cutoff")
    int deleteUnusableCreatedBefore(@Param("now") Instant now, @Param("cutoff") Instant cutoff);
}

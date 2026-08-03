package com.linkflow.auth.domain.repository;

import com.linkflow.auth.domain.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Retires every outstanding reset token for a user. Requesting a new link therefore revokes
     * any previous one, so a link intercepted earlier cannot still be redeemed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetToken t set t.used = true "
            + "where t.userId = :userId and t.used = false")
    int markAllUnusedAsUsedForUser(@Param("userId") UUID userId);

    /**
     * Removes tokens that can no longer be redeemed and are older than the retention window,
     * bounding growth of this table while keeping recent history for support queries.
     */
    @Modifying
    @Query("delete from PasswordResetToken t "
            + "where (t.used = true or t.expiresAt < :now) and t.createdAt < :cutoff")
    int deleteUnusableCreatedBefore(@Param("now") Instant now, @Param("cutoff") Instant cutoff);
}

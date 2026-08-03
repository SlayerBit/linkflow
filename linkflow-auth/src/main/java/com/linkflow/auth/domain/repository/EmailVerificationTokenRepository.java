package com.linkflow.auth.domain.repository;

import com.linkflow.auth.domain.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Retires every outstanding token for a user so that issuing a new link invalidates older
     * ones. Prevents a mailbox from accumulating several simultaneously valid links.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailVerificationToken t set t.used = true "
            + "where t.userId = :userId and t.used = false")
    int markAllUnusedAsUsedForUser(@Param("userId") UUID userId);

    /**
     * Removes tokens that can no longer be redeemed and are older than the retention window.
     * <p>
     * Rows are kept for a while after becoming unusable so that "this link was already used"
     * can still be distinguished from "this link never existed" when a user reports a problem.
     * Without this the table grows for the lifetime of the deployment.
     */
    @Modifying
    @Query("delete from EmailVerificationToken t "
            + "where (t.used = true or t.expiresAt < :now) and t.createdAt < :cutoff")
    int deleteUnusableCreatedBefore(@Param("now") Instant now, @Param("cutoff") Instant cutoff);
}

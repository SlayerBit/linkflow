package com.linkflow.user.domain.repository;

import com.linkflow.user.domain.entity.EmailChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, UUID> {

    Optional<EmailChangeRequest> findByTokenHash(String tokenHash);
}

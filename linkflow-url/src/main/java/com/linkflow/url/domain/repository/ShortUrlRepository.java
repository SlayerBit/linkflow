package com.linkflow.url.domain.repository;

import com.linkflow.url.domain.entity.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, UUID> {

    @Query("SELECT s FROM ShortUrl s WHERE s.shortCode = :shortCode")
    Optional<ShortUrl> findByShortCode(@Param("shortCode") String shortCode);

    @Query("SELECT s FROM ShortUrl s WHERE s.id = :id AND s.deleted = false")
    Optional<ShortUrl> findByIdAndNotDeleted(@Param("id") UUID id);

    boolean existsByShortCode(String shortCode);

    @Query("SELECT s FROM ShortUrl s WHERE s.ownerId = :ownerId AND s.deleted = false")
    Page<ShortUrl> findByOwnerIdAndNotDeleted(@Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("SELECT s FROM ShortUrl s WHERE s.deleted = false")
    Page<ShortUrl> findAllNotDeleted(Pageable pageable);

    @Query("SELECT s FROM ShortUrl s WHERE s.expiresAt < :now AND s.active = true AND s.deleted = false")
    List<ShortUrl> findExpiredActive(@Param("now") Instant now);
}

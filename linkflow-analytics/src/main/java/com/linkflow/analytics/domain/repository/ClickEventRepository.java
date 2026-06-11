package com.linkflow.analytics.domain.repository;

import com.linkflow.analytics.domain.entity.ClickEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID> {

    List<ClickEvent> findByShortUrlIdOrderByClickedAtDesc(UUID shortUrlId, Pageable pageable);
}

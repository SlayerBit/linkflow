package com.linkflow.url.infrastructure.adapter;

import com.linkflow.common.port.UrlStatsPort;
import com.linkflow.url.domain.repository.UrlStatsNativeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UrlStatsAdapter implements UrlStatsPort {

    private final UrlStatsNativeRepository urlStatsNativeRepository;

    @Override
    @Transactional(readOnly = true)
    public long countTotalUrls() {
        return urlStatsNativeRepository.countTotalUrls();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveUrls() {
        return urlStatsNativeRepository.countActiveUrls();
    }

    @Override
    @Transactional(readOnly = true)
    public long countInactiveUrls() {
        return urlStatsNativeRepository.countInactiveUrls();
    }

    @Override
    @Transactional(readOnly = true)
    public long countExpiredUrls() {
        return urlStatsNativeRepository.countExpiredUrls();
    }

    @Override
    @Transactional(readOnly = true)
    public long countDeletedUrls() {
        return urlStatsNativeRepository.countDeletedUrls();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findOwnerIdByShortUrlId(UUID shortUrlId) {
        return urlStatsNativeRepository.findOwnerIdByShortUrlId(shortUrlId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findShortCodeByShortUrlId(UUID shortUrlId) {
        return urlStatsNativeRepository.findShortCodeByShortUrlId(shortUrlId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopUrlData> findTopByOwnerId(UUID ownerId, int limit) {
        return urlStatsNativeRepository.findTopByOwnerId(ownerId, limit).stream()
                .map(p -> new TopUrlData(p.getShortUrlId(), p.getShortCode(), p.getTotalClicks()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopUrlData> findTopSystemWide(int limit) {
        return urlStatsNativeRepository.findTopSystemWide(limit).stream()
                .map(p -> new TopUrlData(p.getShortUrlId(), p.getShortCode(), p.getTotalClicks()))
                .toList();
    }
}

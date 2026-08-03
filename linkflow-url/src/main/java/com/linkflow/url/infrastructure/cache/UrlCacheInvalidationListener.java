package com.linkflow.url.infrastructure.cache;

import com.linkflow.url.application.service.QrCodeService;
import com.linkflow.url.domain.event.UrlMutatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts cached redirect data once the change that invalidated it is durable.
 * <p>
 * Evicting inside the transaction looks safer but is the opposite. Between the eviction and the
 * commit, a concurrent redirect can miss the cache, read the row in its pre-change state, and
 * repopulate the cache with what it saw. The commit then lands with the cache already holding
 * stale data, and it stays that way for the full TTL — fifteen minutes of serving a deleted or
 * retargeted link. Creation has the mirror-image problem: a redirect can re-cache the not-found
 * sentinel before the insert commits, so a brand new link 404s until that entry expires.
 * <p>
 * Waiting for commit narrows the window to the gap between commit and eviction, during which the
 * cache holds a value that was correct moments earlier. If the transaction rolls back the listener
 * never runs, which is right: nothing changed, so nothing needs evicting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UrlCacheInvalidationListener {

    private final UrlCacheService urlCacheService;
    private final QrCodeService qrCodeService;

    /**
     * {@code fallbackExecution} covers callers that mutate outside a transaction; without it the
     * eviction would be skipped silently and the cache would serve stale data indefinitely.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUrlMutated(UrlMutatedEvent event) {
        String shortCode = event.shortCode();
        try {
            urlCacheService.evict(shortCode);
            qrCodeService.evict(shortCode);
        } catch (Exception ex) {
            // The commit already happened; failing here would not undo it. Cache entries carry a
            // TTL, so the worst case is bounded staleness rather than a lost write.
            log.warn("Failed to evict caches for shortCode={} after commit: {}",
                    shortCode, ex.getMessage());
        }
    }
}

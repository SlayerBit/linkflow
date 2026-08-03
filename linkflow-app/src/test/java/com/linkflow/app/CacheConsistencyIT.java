package com.linkflow.app;

import com.linkflow.app.support.AbstractIntegrationTest;
import com.linkflow.url.domain.event.UrlMutatedEvent;
import com.linkflow.url.infrastructure.cache.UrlCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down <em>when</em> cached redirect data is evicted, which is the part that is easy to get
 * wrong and impossible to notice in single-threaded use.
 * <p>
 * Evicting during the transaction leaves a window in which a concurrent redirect can reload the
 * pre-change row and re-cache it, so the commit lands behind an already-stale cache entry that
 * then survives for the full TTL. These tests fail against that implementation.
 */
class CacheConsistencyIT extends AbstractIntegrationTest {

    @Autowired
    private UrlCacheService urlCacheService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void cacheSurvivesUntilTheTransactionCommits() {
        String shortCode = uniqueShortCode();
        urlCacheService.putNegative(shortCode);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new UrlMutatedEvent(shortCode));

            assertTrue(urlCacheService.get(shortCode).isPresent(),
                    "Cache was evicted before commit. A concurrent reader could now repopulate it "
                            + "from the pre-commit row and leave the stale value in place.");
        });

        assertTrue(urlCacheService.get(shortCode).isEmpty(),
                "Cache was not evicted after commit, so the stale entry would be served until it expired");
    }

    @Test
    void cacheIsLeftAloneWhenTheTransactionRollsBack() {
        String shortCode = uniqueShortCode();
        urlCacheService.putNegative(shortCode);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new UrlMutatedEvent(shortCode));
            status.setRollbackOnly();
        });

        // Nothing was written, so the cached value is still accurate and evicting it would only
        // cost a needless database read.
        assertTrue(urlCacheService.get(shortCode).isPresent(),
                "Cache was evicted for a transaction that rolled back");
    }

    @Test
    void evictionStillHappensOutsideATransaction() {
        String shortCode = uniqueShortCode();
        urlCacheService.putNegative(shortCode);

        eventPublisher.publishEvent(new UrlMutatedEvent(shortCode));

        // Without fallback execution the listener would silently do nothing here, and any caller
        // that mutates outside a transaction would serve stale data indefinitely.
        assertTrue(urlCacheService.get(shortCode).isEmpty(),
                "Eviction was skipped because no transaction was active");
    }

    private static String uniqueShortCode() {
        return "cc" + UUID.randomUUID().toString().substring(0, 8);
    }
}

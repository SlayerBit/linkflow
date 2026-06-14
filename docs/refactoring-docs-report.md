# LinkFlow Refactoring Documentation Change Report

This report summarizes the documentation updates completed during the LinkFlow production-grade refactoring. All updates correspond directly to the code changes implemented and validated in the repository.

---

## 1. Summary of Architectural Changes

The following changes were implemented to enhance stability, concurrency control, and scalability:

*   **Atomic Lua Unlock (`unlock.lua`):** Replaced non-atomic GET-then-DEL distributed locks with a single atomic Lua script to prevent race conditions during lock release.
*   **Sliding-Window Rate Limiter (`rate_limiter.lua`):** Swapped the fixed-window RPM counter (vulnerable to boundary bursts) with a sorted-set sliding-window rate limiter using Redis microsecond-precision scores.
*   **Redirect Cache Enhancements (`UrlCacheService`):**
    *   **TTL Jitter:** Added ±20% jitter to both positive and negative TTLs to prevent synchronized cache stampedes.
    *   **Negative Caching:** Invalid shortcodes are cached as negative sentinels for 90s (plus jitter) to shield PostgreSQL from repeating invalid code lookups.
    *   **Stale-While-Revalidate (SWR):** Cache entries support serving stale data instantly while kicking off an async DB update in the background. Negative entries bypass SWR.
*   **Cache Stampede Protection (`RedirectService`):** Added a distributed lock (`lock:cache_refresh:{code}`) during cache misses. Populator acquires the lock and fetches from the DB, while concurrent requests retry cache reading up to 3 times with 100ms backoff before falling back to direct DB queries.
*   **Buffered Analytics (`ClickTrackingService` & `AnalyticsFlushService`):**
    *   **Redis Streams:** Click events are buffered into a Redis stream (`analytics:clicks:stream`) instead of immediate SQL inserts.
    *   **Scheduled Flushing:** A Scheduled job drains the stream using a consumer group, bulk-inserts events into PostgreSQL, and acknowledges them in batches of 1000.
    *   **Scale-Safe Counter Updates:** Per-URL click counts are updated in Redis hashes and tracked via a Redis set (`analytics:active_urls`), eliminating the need for `KEYS` or `SCAN`. Values are atomically decremented/updated to PostgreSQL.
*   **Negative Cache Eviction (`UrlService`):** Creating a new URL now evicts any negative cache entry immediately, ensuring the short code resolves without waiting for the 90-second cache TTL to expire.

---

## 2. Updated Documents

The following repository documents have been updated to maintain consistency with the new architecture:

| Document | Updated Sections | Purpose |
|----------|------------------|---------|
| [ADR-003: Redis](../docs/adr/003-redis.md) | Decisions, Problem, Context, Alternatives, Consequences | Captures design decisions for sliding-window rate limiter, SWR caching with stampede protection, negative caching, and Redis Stream buffering. |
| [System Design](../docs/system-design.md) | Redirect Sequence, Analytics Flow | Updates Mermaid sequence diagrams to reflect SWR caching, stampede locks, and Redis Stream buffering/flushing mechanisms. |
| [Architecture](../docs/architecture.md) | Key Design Decisions Table | Reflects the updated caching, rate limiting, and analytics details. |
| [Code Walkthrough](../docs/code-walkthrough.md) | RateLimitFilter, Redirect Flow, Redis Interactions, Analytics Processing, Scheduled Jobs | Provides step-by-step trace mapping of the new service interactions, scheduler, and Lua scripts. |
| [Interview Prep](../docs/interview-prep.md) | Elevator pitches, design questions, URLs/Redirects, Analytics, Subsystem rapid-fire, Alternatives table | Provides updated, repository-aligned answers for system design interviews regarding LinkFlow's buffering, caching, and rate limiting changes. |
| [Feature Matrix](../docs/feature-matrix.md) | Public redirect, Click tracking, Rate limiting rows | Updates entries to detail Redis Stream buffers, Lua sorted sets, and SWR cache features. |

---

## 3. Implementation and Test Mapping

All changes have been validated against the existing unit and integration test suites:

*   **Locking & Limiting Unit Tests:** Verified in `RateLimitServiceTest` and `UrlCacheServiceTest`.
*   **Redirect Flow Unit Tests:** Verified in `RedirectServiceTest`.
*   **Integration Tests (`linkflow-app`):** Verified via Testcontainers in `AnalyticsAndCacheIT` and `RateLimitIT`.

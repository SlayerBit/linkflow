# ADR-003: Redis for Caching, Rate Limiting, Locks, and Analytics Buffering

## Status

Accepted (Updated for sliding-window rate limiting, SWR caching, and Redis Streams analytics buffering)

## Context

Redirect lookups are read-heavy and prone to cache stampedes. API endpoints need strict, burst-tolerant rate limiting. Custom alias creation is prone to registration race conditions. Click analytics generate high write volume that must be decoupled from the visitor redirect path to minimize response latency.

## Problem

How to optimize hot read paths, enforce fair API quotas, handle lock contention, and buffer click tracking events efficiently without overloading PostgreSQL?

## Decision

Use **Redis 7** for:

1. **Redirect cache-aside with SWR & Stampede Protection** — `UrlCacheService` (key: `url:shortcode:{code}`):
   - **Base TTL:** 15 minutes (with ±20% jitter to prevent synchronized expiration).
   - **Extended Redis TTL:** 30 minutes to support Stale-While-Revalidate (SWR), allowing stale entries to serve instantly while triggering an async background DB refresh.
   - **Negative Caching:** Invalid shortcodes are cached as negative entries for 90 seconds (with ±20% jitter) to prevent DB queries on non-existent codes.
   - **Stampede Protection:** Misses use a distributed refresh lock. One request rebuilds the cache while concurrent requests retry cache lookup (up to 3 times with 100ms backoff) before falling back to the DB.
2. **Sliding-window rate limiting** — `RateLimitService` (keys: `rate_limit:user:{userId}` or `rate_limit:ip:{ipAddress}`):
   - Uses a Redis sorted set per user/IP. Timestamps are stored in microseconds as scores, and members are assigned a random UUID.
   - Prunes expired members and counts current requests atomically via a Lua script (`rate_limiter.lua`), solving the boundary-burst issue of fixed-window counters.
3. **Distributed locks** — `RedisLockService` (keys: `lock:alias:{name}` or `lock:cache_refresh:{code}`):
   - Uses `SET NX EX` for lock acquisition.
   - Releasing the lock uses an atomic compare-and-delete Lua script (`unlock.lua`) to guarantee that only the owner can release the lock.
4. **Buffered click tracking** — `ClickTrackingService` (key: `analytics:clicks:stream`):
   - Decouples click event persistence from redirects by appending events to a Redis Stream.
   - Tracked URL IDs with pending increments are stored in a Redis Set (`analytics:active_urls`) to avoid expensive SCAN/KEYS operations.
   - A scheduled background job (`AnalyticsFlushService`) drains the stream using a consumer group, bulk-inserts events, and atomically decrements/updates the DB counters.

Spring Data Redis is configured in `RedisConfig` (`linkflow-common`).

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| PostgreSQL only | Higher latency on redirect hot path; poor fit for high-frequency rate-limiting writes; DB query overhead for click events. |
| Caffeine local cache | Not shared across app instances; cannot coordinate global rate limits or distributed locks. |
| Redis Lists for analytics | Lacked consumer groups, automatic entry ID generation, and consumer acknowledgement tracking. |
| Fixed-window rate limiting | Boundary bursts allow up to 2x the limit at the turn of the minute. |

## Consequences

**Positive:**
- Extremely fast redirects (sub-millisecond cache hits).
- Precise, concurrency-safe sliding-window rate limits.
- Reduced PostgreSQL write pressure via buffered Redis Stream batch-inserting click events.
- Resilient design: rate limiting and analytics fallback to direct DB writes if Redis goes down.

**Negative:**
- Additional infrastructure dependency (Redis).
- Increased complexity from cache-invalidation (e.g. evicting negative cache on shortcode creation).
- Rate limit **fail-open** for most paths under Redis outage, except `/api/v1/auth/**` which **fails closed (503)** to prevent brute-force attacks.

## References

- `UrlCacheService`, `RateLimitService`, `RedisLockService`, `ClickTrackingService`, `AnalyticsFlushService`
- `rate_limiter.lua`, `unlock.lua`
- [system-design.md](../system-design.md)


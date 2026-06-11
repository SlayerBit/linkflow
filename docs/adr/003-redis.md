# ADR-003: Redis for Cache, Rate Limiting, and Locks

## Status

Accepted

## Context

Redirect lookups are read-heavy. API endpoints need rate limiting. Custom alias creation has race conditions.

## Problem

How to optimize hot paths and enforce quotas without overloading PostgreSQL?

## Decision

Use **Redis 7** for:

1. **Redirect cache-aside** — `UrlCacheService`, key `url:shortcode:{code}`, TTL 15 min
2. **Rate limiting** — Lua script atomic counters, keys per user/IP per minute
3. **Distributed locks** — `RedisLockService` for alias creation, TTL 10s

Spring Data Redis configured in `RedisConfig` (`linkflow-common`).

## Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| PostgreSQL only | Higher latency on redirect hot path; poor fit for TTL counters |
| Caffeine local cache | Not shared across app instances |
| Gateway rate limiting only | App also reachable on 8081; defense in depth at servlet layer |

## Consequences

**Positive:** Fast redirects, accurate distributed rate limits, reduced DB read load.

**Negative:** Additional infrastructure; cache invalidation complexity; rate limit **fail-open** for most paths when Redis is unavailable; **fail-closed (503)** on `/api/v1/auth/**` by default to protect login/register.

## References

- `UrlCacheService`, `RateLimitService`, `RedisLockService`
- `rate_limiter.lua`
- [system-design.md](../system-design.md)

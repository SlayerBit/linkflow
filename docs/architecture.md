# Architecture

> **Canonical document:** [system-design.md](system-design.md) — read that for full diagrams, flows, and tradeoffs.

LinkFlow is a modular monolith with three runnable Spring Boot processes:

| Process | Port | Role |
|---------|------|------|
| `linkflow-gateway` | 8080 | Public entry — routes `/api/**`, `/r/**`, Swagger, and web UI |
| `linkflow-app` | 8081 | Assembles all feature modules |
| `linkflow-web` | 8082 | Thymeleaf UI — proxied at `/` via gateway |

## Module dependency rule

Feature modules depend **only** on `linkflow-common`. Cross-module communication uses ports:

- `UserLookupPort` — auth ↔ user (`UserLookupAdapter`)
- `ClickTrackingPort` — url ↔ analytics (`ClickTrackingAdapter`)

## Request flow

```
Browser → Gateway (:8080) → App (:8081) for API/redirects
Browser → Gateway (:8080) → Web (:8082) for pages
Web UI → Gateway (:8080) → App (:8081) for API calls
```

Correlation IDs: `CorrelationIdGatewayFilter` at gateway → `X-Correlation-ID` in responses.

## Key design decisions

| Topic | Detail |
|-------|--------|
| Auth | JWT access (15 min) + opaque refresh (30 days) with rotation |
| Cache | Redis cache-aside with ±20% TTL jitter, negative caching (90s TTL), stale-while-revalidate (SWR), and lock-based stampede protection |
| Rate limit | Redis-backed sliding-window (Lua script + sorted sets). Auth paths fail-closed; others fail-open |
| Analytics | `@Async` click tracking buffered in Redis Streams, periodically flushed (30s delay) via consumer group to PostgreSQL |
| Security | Profile-based actuator/Swagger exposure |
| Schema | Flyway in `linkflow-app` (V1–V6) |

ADRs: [adr/](adr/)

## Related

- [module-dependency-map.md](module-dependency-map.md)
- [docker.md](docker.md) — full Compose stack

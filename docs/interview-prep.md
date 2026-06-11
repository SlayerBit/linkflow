# LinkFlow Interview Preparation

Repo-specific preparation grounded in the LinkFlow codebase. Answers are written to be spoken aloud in a system design or backend interview — not one-line trivia.

**Canonical references:** [system-design.md](system-design.md), [api-inventory.md](api-inventory.md), [feature-matrix.md](feature-matrix.md)

---

## Project pitch (30 seconds)

LinkFlow is a production-style URL shortener built as a **modular monolith** in Java 21. Authenticated users create short links with optional custom aliases and expiry; anonymous visitors hit `GET /r/{code}` for a fast redirect. The system uses PostgreSQL for durable state, Redis for redirect caching and rate limiting, JWT plus rotating refresh tokens for auth, and async analytics so redirects stay low-latency. Three Spring Boot processes — gateway, backend app, and Thymeleaf web UI — compose the full product behind a single public port.

---

## Elevator pitches

### 2-minute version

LinkFlow demonstrates how to build a real URL-management platform without premature microservices. Seven feature modules (`auth`, `user`, `url`, `rate-limit`, `analytics`, `observability`, plus `common`) compile independently but deploy as one backend JAR (`linkflow-app` on 8081). Spring Cloud Gateway (8080) is the public entry: API, redirects, Swagger, and the web UI all share one host.

Security is stateless JWT on the API with opaque refresh tokens stored as SHA-256 hashes in PostgreSQL — rotation on refresh, revoke-all on reuse. Redirects use Redis cache-aside (15-minute TTL) with PostgreSQL fallback. Click tracking is `@Async` so the redirect path never waits on analytics writes.

Why this shape? It shows module boundaries, port/adapter integration, idempotency, observability, and production tradeoffs (fail-open vs fail-closed rate limiting) in a codebase small enough to explain in an interview.

### 5-minute version

Add to the above:

**Module rule:** Feature modules depend only on `linkflow-common`. They never compile against each other. Cross-module calls use ports: `UserLookupPort` (auth → user) and `ClickTrackingPort` (url → analytics). `linkflow-app` wires adapters at runtime.

**Redirect path:** Gateway → `RedirectController` → `RedirectService` → Redis `url:shortcode:{code}` or PostgreSQL → `ClickTrackingPort.trackClick` (async) → 302 response.

**Rate limiting:** `RateLimitFilter` after JWT filter. Authenticated users: 100 RPM per user. Anonymous: 200 RPM per IP. Auth paths (`/api/v1/auth/**`) **fail closed** (503) if Redis is down — protects login from abuse during outages. Everything else **fail open** — availability over strict throttling.

**Web UI:** Separate process (`linkflow-web`, 8082) with no backend compile deps. Server-side `HttpSession` stores JWTs; browser never sees tokens. All API calls go through the gateway via `RestClient`.

**Ops:** Flyway owns schema (V1–V6). Docker Compose runs postgres, redis, app, web, gateway, Prometheus, Grafana. Nine integration test classes use Testcontainers.

### 10-minute version

Add:

**Data model:** Users and roles (USER, ADMIN), refresh tokens, short URLs with soft delete, `url_analytics` aggregates, `click_events` raw rows, `idempotency_records` for safe retries.

**Admin surface:** REST API supports user disable/enable/delete, URL deactivation, system stats, recent click listing. Web admin UI covers listing and URL deactivation — user lifecycle actions are API-only (deliberate v1 scope).

**Security profiles:** Dev exposes Swagger and actuator broadly. Prod disables Swagger, restricts actuator to health (+ optional metrics via `LINKFLOW_METRICS_PUBLIC`). `JwtSecretValidator` fails fast on weak secrets in prod.

**Tradeoffs accepted:** Modular monolith over microservices; aggregate + recent-click analytics over time-series rollups; in-memory web sessions over Redis sessions; gateway as routing-only (no auth at edge).

**ADRs:** See `docs/adr/` for PostgreSQL, Redis, JWT, gateway, Flyway, observability decisions.

---

## Design choice questions (“why this, not that?”)

### Why was this architecture chosen?

LinkFlow is an **interview-grade reference implementation**, not a hyperscale product. The goal is to demonstrate production patterns — auth, caching, rate limits, migrations, observability — in a codebase you can navigate in one session. A modular monolith with a thin gateway gives clear module boundaries without the operational cost of six deployables. The three-process split (gateway + app + web) still shows how real products separate edge routing, API, and presentation tiers.

**Follow-up:** “Would you merge web into app?” — Possible with Spring MVC in the same JAR, but separation keeps Thymeleaf/session concerns out of the stateless API and avoids compile-time coupling.

### Why modular monolith instead of microservices?

**Cohesion:** URL creation, ownership checks, redirect resolution, and click attribution share one database and often one transaction boundary. Splitting would force distributed transactions or sagas for flows that today are a single `@Transactional` service method.

**Scale:** At demo/interview scale, network hops between services add latency and failure modes without benefit. Modules already enforce boundaries via Maven and port interfaces — you could extract `linkflow-analytics` later because `ClickTrackingPort` is async and loosely coupled.

**Alternative considered:** Single flat Spring Boot app with packages only — rejected because Maven modules make dependency rules enforceable (`linkflow-url` cannot import `linkflow-auth`).

### Why PostgreSQL instead of MongoDB or DynamoDB?

URLs, users, and tokens need **referential integrity** (owner FK, unique short codes, refresh token ownership). ACID transactions matter for refresh rotation (revoke old + insert new) and idempotency replay. Flyway gives versioned, reviewable schema migrations. PostgreSQL JSON could store flexible analytics later, but relational modeling fits v1.

**Follow-up:** “What about wide-column for redirects?” — Redis already handles the hot read path; PostgreSQL is the source of truth with `lower(short_code)` index.

### Why Redis instead of in-memory caching?

Three distinct needs: (1) **shared redirect cache** across app instances, (2) **distributed rate limit counters** with minute buckets, (3) **short-lived alias locks** during custom alias creation. Caffeine (present in url module) could supplement local caching but cannot coordinate rate limits or locks across pods.

**Follow-up:** “Why not PostgreSQL advisory locks for aliases?” — Possible; Redis 10-second TTL locks are simpler and keep contention off the primary DB.

### Why JWT plus refresh tokens?

**Access tokens (JWT, 15 min):** Stateless validation on every API request — no session store lookup. Claims carry `userId`, `email`, `roles` for authorization.

**Refresh tokens (opaque, 30 days):** Long-lived credentials stay revocable. Stored as SHA-256 hash only; rotation limits stolen refresh window; reuse detection revokes all user sessions.

**Alternative considered:** Session cookies for API — rejected for stateless scaling and clear separation from web UI sessions.

**Follow-up:** “Why not JWT refresh tokens?” — Opaque tokens allow server-side revocation and rotation tracking in `refresh_tokens.replaced_by_token_hash` without parsing JWT expiry client-side.

### Why gateway routing here?

Single public URL for browser (`/`), API (`/api/**`), and redirects (`/r/**`). Correlation ID injection at the edge. Future TLS termination, WAF, or path-based routing without touching business controllers. JWT validation stays in the app — one source of truth, gateway stays dumb.

**Alternative considered:** Nginx reverse proxy — fine in production; Spring Cloud Gateway demonstrates Java-native edge routing for interviews.

**Follow-up:** “Why not proxy app actuator?” — Prometheus scrapes `linkflow-app:8081` directly; gateway health stays independent of backend DB/Redis failures.

### Why async analytics?

Redirect latency is user-visible. Writing `click_events` + updating `url_analytics` on the hot path would add DB round-trips to every anonymous click. `@Async ClickTrackingService` with a bounded pool (core 2, max 8, queue 500) decouples tracking from 302 response. Lost clicks under overload are an accepted tradeoff — logged, not fatal to redirect.

**Follow-up:** “How do you guarantee exactly-once clicks?” — Not guaranteed; analytics is best-effort. Idempotency applies to URL **creation**, not clicks.

### Why fail-open vs fail-closed for rate limiting?

| Path | Redis down | Rationale |
|------|------------|-----------|
| `/api/v1/auth/**` | **503 fail-closed** | Login/register are abuse magnets; allowing unlimited auth attempts during Redis outage is worse than temporary unavailability |
| Authenticated API, redirects | **Fail-open** | Product remains usable; redirects are revenue/UX critical |
| Rate limit exceeded (Redis up) | **429** | Normal enforcement |

Configurable via `LINKFLOW_RATE_LIMIT_AUTH_FAIL_CLOSED` (default `true`). Tested in `AuthRateLimitRedisDownIT`.

### Why this role model?

Two fixed roles (`USER`, `ADMIN`) seeded in Flyway V1. Stored as `user_roles` join table with `@ElementCollection` on `User` — avoids `@ManyToMany` entity complexity for two static roles. JWT embeds role names at login; changes require re-login or refresh (acceptable at this scale).

**Deliberate non-goal:** Runtime role assignment API — admin promotion is bootstrap or direct DB only.

### Why this deployment approach?

Docker Compose-first for reproducible demos: one command brings up the full stack including web UI and observability. Kubernetes manifests are intentionally out of repo scope — consumers bring their own orchestration. Three Dockerfiles (`Dockerfile.app`, `.gateway`, `.web`) multi-stage build from Maven.

### Why these tests?

- **Unit tests** in auth, url, rate-limit, common — fast feedback on algorithms (JWT, cache, Lua limiter).
- **Integration tests** (8 in app + `GatewayRoutingIT`) — Testcontainers PostgreSQL/Redis prove real wiring: auth flow, URL CRUD, rate limits, admin authz, actuator exposure, analytics cache, auth fail-closed.
- **No web UI Selenium tests** — deliberate scope; web is thin BFF over tested API.

**Follow-up:** “What’s missing?” — Contract tests between gateway routes and controllers; chaos tests for Redis/Postgres failure; load tests for redirect path.

### Why this documentation structure?

One **canonical doc per topic** (`system-design.md`, `api-inventory.md`, `database-design.md`) with pointers elsewhere to avoid drift. ADRs capture decisions; `feature-matrix.md` maps features to code. `implementation_plan.md` is archived — historical spec only.

---

## Architecture deep dive

**Q: Name the three runnable applications and ports.**  
`linkflow-gateway` (8080), `linkflow-app` (8081), `linkflow-web` (8082).

**Q: What paths does the gateway route?**  
To **app:** `/api/**`, `/r/**`, `/swagger-ui/**`, `/v3/api-docs/**`. To **web:** `/css/**`, `/js/**`, `/webjars/**`, catch-all `/**`. Gateway `/actuator/**` is local only — not proxied to app.

**Q: Is linkflow-web in Docker Compose?**  
Yes — service `linkflow-web` on port 8082, depends on gateway, `LINKFLOW_GATEWAY_URL=http://linkflow-gateway:8080`.

**Q: How many Maven modules?**  
Eleven including parent: common, auth, user, url, rate-limit, analytics, observability, app, gateway, web.

**Q: How do modules communicate?**  
Port interfaces in `linkflow-common`: `UserLookupPort`, `ClickTrackingPort`. Adapters in owning modules; wired by Spring in `linkflow-app`.

---

## Authentication & JWT

**Q: Walk through login.**  
Client POST `/api/v1/auth/login` → gateway → `AuthController` → `AuthService` validates BCrypt password via `UserLookupPort` → `JwtService` issues access JWT → `RefreshTokenService` creates opaque refresh token, stores hash → `TokenResponse`.

**Q: Refresh rotation?**  
POST `/api/v1/auth/refresh` with refresh token → validate hash, not revoked, not expired → revoke old token, store `replaced_by_token_hash` → issue new pair. Reuse of revoked token → revoke **all** user refresh tokens.

**Q: Web UI token storage?**  
`HttpSession` attribute `AUTH_STATE` (`AuthState` record). `SessionAuthFilter` sets SecurityContext. `ApiCallHelper` refreshes on 401 once.

**Q: Why BCrypt strength 12?**  
Balance cost vs UX on login; configurable industry default for password hashing.

---

## URLs, redirects, cache

**Q: Redirect flow?**  
`GET /r/{shortCode}` (public) → `RedirectService` → Redis cache-aside → validate active/not deleted/not expired → async `ClickTrackingPort` → 302.

**Q: Cache key and TTL?**  
`url:shortcode:{lowercase}` — 15 minutes. Evicted on URL update/delete/deactivate.

**Q: Custom alias races?**  
`RedisLockService` key `lock:alias:{normalized}` (~10s) + DB unique on `short_code`.

**Q: Idempotency?**  
Optional `Idempotency-Key` on single create; required on bulk. Keyed `(user_id, endpoint, idempotency_key)` in `idempotency_records`; 24h TTL; hourly cleanup job.

---

## Analytics

**Q: What tables?**  
`click_events` (raw rows with IP, UA, referer), `url_analytics` (per-URL `total_clicks`, `last_accessed_at`).

**Q: Is there a click list API?**  
Yes — `GET /api/v1/urls/{id}/analytics/clicks` (owner, paginated, max 100). Admin: `GET /api/v1/admin/analytics/urls/{id}/clicks`.

**Q: What is NOT implemented?**  
Time-series rollups (hourly/daily charts), geo dashboards, referer aggregation APIs — deliberate v1 non-goals.

---

## Database & migrations

**Q: How many Flyway migrations?**  
Six (V1 users/roles through V6 audit columns on `url_analytics`).

**Q: Hibernate DDL mode?**  
`validate` — Flyway owns schema.

**Q: Role storage?**  
`roles` table + `user_roles` join; `@ElementCollection Set<Long> roleIds` on `User`.

---

## Observability & testing

**Q: Integration test classes?**  
**App (8):** `AuthFlowIT`, `UrlFlowIT`, `RateLimitIT`, `AdminAuthorizationIT`, `AdminUserManagementIT`, `AnalyticsAndCacheIT`, `ActuatorExposureIT`, `AuthRateLimitRedisDownIT`. **Gateway (1):** `GatewayRoutingIT`.

**Q: Actuator exposure in prod?**  
Swagger denied. Actuator mostly denied; `/actuator/health` public; Prometheus/metrics public only if `LINKFLOW_METRICS_PUBLIC=true` (Compose demo sets true).

**Q: Custom health indicator?**  
`RedisHealthIndicator` in `linkflow-observability`.

**Q: Prometheus scrape targets?**  
`linkflow-app:8081`, `linkflow-gateway:8080` — not web.

---

## Docker & deployment

**Q: Compose services?**  
postgres, redis, linkflow-app, linkflow-web, linkflow-gateway, prometheus, grafana.

**Q: Dockerfiles?**  
`docker/Dockerfile.app`, `docker/Dockerfile.gateway`, `docker/Dockerfile.web` — Temurin 21 multi-stage Maven builds.

**Q: Required env for prod app startup?**  
`LINKFLOW_JWT_SECRET` (strong Base64, validated in prod profile).

**Q: macOS gateway tip?**  
`LINKFLOW_APP_URI=http://127.0.0.1:8081` — avoids IPv6 `localhost` → `::1` connection refused.

---

## Production readiness questions

**Q: Biggest security gaps for real production?**  
CORS wildcard default (`*`), demo Compose credentials, bootstrap admin enabled by default in Compose, JWT roles stale until refresh, unbounded `click_events` growth without retention job.

**Q: How would you harden for prod?**  
Lock CORS, disable bootstrap admin, secrets manager for JWT, HTTPS + secure cookies, network-restrict actuator, WAF/rate limit at edge, click event retention, Redis/Postgres HA.

**Q: Horizontal scaling?**  
Multiple `linkflow-app` instances behind gateway; shared Postgres + Redis. Web tier needs sticky sessions or Spring Session Redis (not implemented). Redirect cache and rate limits already shared via Redis.

**Q: What would you split into microservices first?**  
Analytics (already async via port) or auth — but only with clear ownership boundaries and acceptance of distributed ops cost.

---

## Debugging & failure scenarios

**Q: Gateway 500 to app on macOS?**  
Set `LINKFLOW_APP_URI=http://127.0.0.1:8081`.

**Q: `role "linkflow" does not exist`?**  
Native Postgres on 5432 conflicting with Docker — stop Homebrew postgres or remap port.

**Q: Redis down — what breaks?**  
Rate limits fail-open (except auth → 503). Redirect cache misses go to PostgreSQL (slower but works). Redis health shows DOWN in actuator.

**Q: Refresh token reuse detected — user impact?**  
All refresh tokens revoked; user must log in again — indicates possible token theft.

**Q: Schema validation error on `url_analytics.created_by`?**  
Run Flyway V6 or reset DB volume.

**Q: Integration tests fail locally?**  
Docker Desktop must be running for Testcontainers.

**Q: Swagger 404 via gateway in prod?**  
Expected — `swagger-public=false` in prod profile.

---

## Subsystem rapid-fire (with follow-ups)

| Subsystem | One-line | Likely follow-up |
|-----------|----------|------------------|
| Gateway | YAML routes + correlation ID | Route order: API before web catch-all |
| Auth | JWT + opaque refresh rotation | Why HMAC-SHA512? Symmetric simplicity for monolith |
| URL | CRUD + redirect + QR + idempotency | Why soft delete? Audit trail + prevent code reuse |
| Rate limit | Redis Lua, user vs IP buckets | Why skip actuator/swagger? Ops endpoints shouldn't consume user quota |
| Analytics | Async insert + aggregate increment | Eventual consistency on `total_clicks` — acceptable for v1 |
| Web | Session BFF, RestClient to gateway | Why not WebClient? RestClient simpler for blocking SSR |
| Observability | Actuator + Prometheus + Grafana | Why not scrape web? Minimal custom metrics on web tier |

---

## Alternatives considered (summary table)

| Decision | Chosen | Rejected alternative |
|----------|--------|----------------------|
| Architecture | Modular monolith | Microservices, flat monolith |
| Database | PostgreSQL + Flyway | MongoDB, Hibernate ddl-auto |
| Hot path cache | Redis | DB-only, local Caffeine only |
| API auth | JWT + opaque refresh | Session cookies, JWT refresh tokens |
| Edge | Spring Cloud Gateway | Direct app, Nginx-only |
| Analytics write | @Async best-effort | Sync write on redirect |
| Rate limit Redis down | Fail-open except auth | Fail-closed everywhere |
| Web auth | Server session | SPA + localStorage JWT |
| Analytics API | Aggregates + recent list | Full time-series platform |
| K8s manifests | Out of repo | In-repo Helm charts |

---

## What would you change at larger scale?

1. **Read replicas** for redirect lookups and analytics queries; keep writes on primary.
2. **CDN** in front of `GET /r/**` with short TTL — reduce origin load globally.
3. **Spring Session Redis** or sticky sessions for web tier.
4. **Click event pipeline** — Kafka → Flink/Spark for rollups instead of synchronous aggregate updates.
5. **Edge rate limiting** at gateway/CDN; keep app limits as defense in depth.
6. **Short code generation** — snowflake IDs or dedicated counter service if collision rates matter.
7. **Retention job** for `click_events` and partitioned tables by month.

---

## Deliberate non-goals (final)

- Kubernetes manifests in repo
- Redis-backed web sessions
- Runtime role assignment API
- Time-series analytics / chart API
- Email verification / OAuth social login
- HTTPS/TLS in application config (terminate at LB)
- Bulk URL create or admin user lifecycle in web UI
- Guaranteed exactly-once click tracking

See [system-design.md](system-design.md#deliberate-non-goals) and [feature-matrix.md](feature-matrix.md).

---

## Common whiteboard prompts

1. **Draw redirect sequence** — Use [system-design.md](system-design.md) redirect diagram: cache-aside → async analytics → 302.
2. **Draw auth sequence** — Login → JWT + refresh hash in DB → subsequent Bearer request through filters.
3. **Draw module graph** — All feature modules → common; app assembles; gateway/web separate.
4. **Rate limit decision tree** — Auth path vs authenticated vs anonymous; Redis up/down branches.

---

## Related documents

- [project-deep-dive.md](project-deep-dive.md) — subsystem narratives
- [system-design.md](system-design.md) — canonical diagrams
- [learning-roadmap.md](learning-roadmap.md) — study order
- [production-readiness-audit.md](production-readiness-audit.md) — audit status
- [security-review.md](security-review.md) — threat analysis

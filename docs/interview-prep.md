# LinkFlow Interview Preparation

Repo-specific Q&A grounded in the LinkFlow codebase. Generic framework tutorials intentionally omitted.

---

## Tell me about this project

LinkFlow is a production-style URL shortener built as a **modular monolith** in Java 21 and Spring Boot 3.4. Three runnable processes exist: `linkflow-gateway` (8080) routes traffic to `linkflow-app` (8081), which assembles feature modules for auth, users, URLs, rate limiting, analytics, and observability. PostgreSQL stores users, tokens, URLs, and analytics; Redis caches redirect lookups and enforces rate limits with a Lua script. JWT access tokens (15 minutes) pair with opaque rotating refresh tokens (30 days). Redirects at `GET /r/{shortCode}` use cache-aside and trigger async click tracking so latency stays low. A separate `linkflow-web` module (8082) provides a Thymeleaf UI that calls the gateway via `RestClient`, storing JWTs in server-side sessions. Integration tests use Testcontainers for PostgreSQL and Redis.

---

## Elevator pitches

### 2-minute version

LinkFlow shortens URLs for authenticated users, tracks clicks, and exposes admin analytics. Architecture is a modular monolith — seven feature JARs wired by `linkflow-app`, not microservices. Spring Cloud Gateway is the public entry. Security is JWT + refresh rotation. Redis handles redirect cache and rate limits. Flyway owns the schema. It's designed to demonstrate real-world patterns: idempotency, soft delete, port/adapter module boundaries, and observability.

### 5-minute version

Add to the above: Module dependency rule — feature modules only depend on `linkflow-common`; cross-module calls use `UserLookupPort` and `ClickTrackingPort`. Walk the redirect path: `RedirectController` → `RedirectService` → Redis cache or PostgreSQL → `ClickTrackingPort` → `@Async ClickTrackingService`. Mention rate limiting after JWT filter — auth paths fail closed when Redis is down; other paths fail open. Note web UI as BFF with session-stored tokens. Docker Compose runs app, gateway, **web**, Postgres, Redis, Prometheus, Grafana. Gateway proxies `/` to web for single-host UX. Tests: unit tests in auth/url/rate-limit; nine integration test classes in linkflow-app; `GatewayRoutingIT` in gateway.

### 10-minute version

Add: Database tables (users, short_urls, url_analytics, click_events, refresh_tokens, idempotency_records). Admin bootstrap via env vars. Admin user disable/enable/delete. Recent click events API. Scheduled `ExpiredUrlCleanupJob`. QR codes via ZXing. Profile-based actuator/Swagger in prod. Design tradeoffs: modular monolith vs microservices, aggregate analytics vs time-series rollups (non-goal), auth fail-closed vs general fail-open rate limiting. Reference ADRs in `docs/adr/`.

---

## Architecture (15 questions)

**Q1. Why a modular monolith instead of microservices?**  
A: LinkFlow's domains (auth, URL, analytics) are cohesive and share one database transaction boundary for many operations. Separate deployables would add network latency and ops complexity without current scale needs. Modules still enforce boundaries via Maven and port interfaces.

**Q2. What are the three runnable applications?**  
A: `linkflow-app` (8081), `linkflow-gateway` (8080), `linkflow-web` (8082).

**Q3. What is the module dependency rule?**  
A: Feature modules depend only on `linkflow-common`, never on each other. `linkflow-app` assembles all feature modules.

**Q4. How do modules communicate?**  
A: Through port interfaces in `linkflow-common`: `UserLookupPort` (auth→user) and `ClickTrackingPort` (url→analytics), implemented by adapters in the owning module.

**Q5. Why does the gateway exist?**  
A: Single public entry point, route aggregation, correlation ID injection — without embedding routing in the monolith.

**Q6. Does the gateway contain business logic?**  
A: No. Only YAML routes and `CorrelationIdGatewayFilter`.

**Q7. What paths does the gateway route?**  
A: `/api/**`, `/r/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` to `LINKFLOW_APP_URI`.

**Q8. Why is linkflow-web separate from linkflow-app?**  
A: Different stack concerns (Thymeleaf SSR vs REST API), no compile-time coupling, session-based UI auth vs stateless JWT API.

**Q9. Is linkflow-web in docker-compose?**  
A: No — run locally as JAR on 8082.

**Q10. What is `LinkFlowApplication` responsible for?**  
A: Spring Boot entry, component scan of `com.linkflow`, JPA auditing/repos, async, scheduling, Flyway migrations.

**Q11. Where do Flyway migrations live?**  
A: `linkflow-app/src/main/resources/db/migration/`.

**Q12. What cross-cutting job runs hourly?**  
A: `ExpiredUrlCleanupJob` — deactivates expired URLs and cleans idempotency records.

**Q13. How are correlation IDs propagated?**  
A: `CorrelationIdGatewayFilter` at gateway; included in `ApiResponse`.

**Q14. What is linkflow-observability's role?**  
A: Actuator/Prometheus dependencies and `RedisHealthIndicator`.

**Q15. What Java version is required?**  
A: JDK 21 — enforced by Maven Enforcer in parent POM.

---

## Authentication & JWT (15 questions)

**Q16. What algorithm signs JWTs?**  
A: HMAC-SHA512 via jjwt in `JwtService`.

**Q17. Default access token TTL?**  
A: 900000 ms (15 minutes) — `linkflow.jwt.access-expiration-ms`.

**Q18. Default refresh token TTL?**  
A: 2592000000 ms (30 days).

**Q19. Are refresh tokens JWTs?**  
A: No — opaque random tokens; SHA-256 hash stored in `refresh_tokens`.

**Q20. What happens on refresh token reuse?**  
A: `RefreshTokenService` revokes all refresh tokens for that user.

**Q21. What password hashing is used?**  
A: BCrypt strength 12 (`BCryptPasswordEncoder(12)`).

**Q22. What claims are in the access token?**  
A: `userId`, `email`, `roles`, `tokenType=access`, plus standard `sub`, `jti`, expiry.

**Q23. Which filter validates JWTs?**  
A: `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`.

**Q24. What happens on invalid JWT?**  
A: `JwtAuthenticationEntryPoint` returns 401 JSON.

**Q25. Is CSRF enabled on the API?**  
A: No — stateless Bearer API.

**Q26. Is CSRF enabled on the web UI?**  
A: Yes, except `POST /tools/rate-limit/probe`.

**Q27. Where does the web UI store tokens?**  
A: `HttpSession` as `AuthState` — not browser localStorage.

**Q28. How does the web UI handle expired access tokens?**  
A: `ApiCallHelper` calls refresh on 401, updates session, retries once.

**Q29. How is the first admin user created?**  
A: `AdminBootstrap` when `LINKFLOW_BOOTSTRAP_ADMIN_ENABLED=true`.

**Q30. What roles exist?**  
A: `USER` and `ADMIN` in `roles` table, mapped to `ROLE_USER` / `ROLE_ADMIN`.

---

## URLs, redirects, cache (15 questions)

**Q31. How are short codes generated?**  
A: `ShortCodeGenerator` using Base62 for auto codes; custom aliases validated by regex.

**Q32. What is the public redirect path?**  
A: `GET /r/{shortCode}` — no authentication.

**Q33. What HTTP status does redirect return?**  
A: 302 to original URL.

**Q34. What Redis key stores cached URLs?**  
A: `url:shortcode:{lowercase}` — TTL 15 minutes.

**Q35. What happens on cache miss?**  
A: Query `ShortUrlRepository.findByShortCode`, populate cache, redirect.

**Q36. When is cache evicted?**  
A: On URL update, delete, deactivate via `UrlCacheService`.

**Q37. What prevents custom alias race conditions?**  
A: `RedisLockService` with key `lock:alias:{normalized}` (~10s TTL).

**Q38. What validates redirect eligibility?**  
A: `RedirectService.validateRedirectable` — not deleted, active, not expired.

**Q39. Does click tracking block redirects?**  
A: No — `@Async` via `ClickTrackingService`; failures logged in `RedirectService`.

**Q40. What is `LINKFLOW_BASE_URL` used for?**  
A: Prefix in `UrlResponse.shortUrl` (e.g. `http://localhost:8080/r/{code}`).

**Q41. How does soft delete work for URLs?**  
A: `ShortUrl.softDelete()` sets `deleted=true`, `active=false`, `deletedAt`.

**Q42. How are QR codes generated?**  
A: `QrCodeService` with ZXing; endpoint returns PNG bytes.

**Q43. Max original URL length?**  
A: 2048 characters (`CreateUrlRequest`).

**Q44. Max bulk create size?**  
A: 100 URLs (validated in `UrlService`).

**Q45. Is original URL scheme validated?**  
A: Yes — `UrlService` validates URI scheme (http/https).

---

## Rate limiting (8 questions)

**Q46. Where is rate limiting implemented?**  
A: `RateLimitFilter` in `linkflow-rate-limit`, registered after JWT filter.

**Q47. Default authenticated limit?**  
A: 100 requests/minute per user (`linkflow.rate-limit.user-rpm`).

**Q48. Default anonymous limit?**  
A: 200 requests/minute per IP.

**Q49. What paths skip rate limiting?**  
A: `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`.

**Q50. What headers are returned?**  
A: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.

**Q51. What status on exceed?**  
A: HTTP 429 — `RateLimitExceededException`.

**Q52. What if Redis is down?**  
A: Auth paths (`/api/v1/auth/**`) fail closed — HTTP 503 (`RATE_LIMIT_BACKEND_UNAVAILABLE`). Other paths fail open (`RateLimitInfo.failOpen()`).

**Q53. Why Lua script?**  
A: Atomic INCR + EXPIRE in Redis (`rate_limiter.lua`).

---

## Analytics & idempotency (10 questions)

**Q54. What tables store analytics?**  
A: `click_events` (raw), `url_analytics` (aggregates).

**Q55. Is there an API to list click events?**  
A: No — verified absent from controllers.

**Q56. What does per-URL analytics return?**  
A: `totalClicks`, `lastAccessedAt`, `shortCode`, `shortUrlId`.

**Q57. Who can read URL analytics?**  
A: Owner only — checked in `AnalyticsQueryService`.

**Q58. What thread pool handles async clicks?**  
A: `clickTrackingExecutor` — core 2, max 8, queue 500 (`AsyncConfig`).

**Q59. What is idempotency keyed on?**  
A: `(user_id, endpoint, idempotency_key)` unique in DB.

**Q60. Is Idempotency-Key required for single create?**  
A: Optional for `POST /api/v1/urls`; required for bulk.

**Q61. Where are idempotent responses stored?**  
A: `idempotency_records.response_body` as serialized JSON.

**Q62. What admin stats are available?**  
A: `SystemStatsResponse` — totalUsers, totalUrls, totalClicks, active/expired/deleted URL counts.

**Q63. Can users see system-wide top URLs?**  
A: No — `/api/v1/analytics/top` is scoped to current user; system-wide is admin only.

---

## Database & JPA (10 questions)

**Q64. What DDL mode does Hibernate use?**  
A: `validate` — schema owned by Flyway.

**Q65. How many Flyway migrations?**  
A: Six (V1–V6).

**Q66. What is `AuditableEntity`?**  
A: Mapped superclass with `createdAt`, `updatedAt`, `createdBy`, `updatedBy`.

**Q67. Why was V6 migration added?**  
A: Add `created_by`, `updated_by` to `url_analytics` for auditing alignment.

**Q68. How are user roles stored?**  
A: `user_roles` join table; JPA `@ElementCollection` on `User.roleIds`.

**Q69. What index optimizes redirect lookup?**  
A: `idx_short_urls_short_code ON lower(short_code)`.

**Q70. Are UUIDs used for primary keys?**  
A: Yes for users, URLs, tokens, events — except `roles.id` (BIGSERIAL).

**Q71. What is `StatsRepository`?**  
A: Custom repository with native SQL aggregates for admin stats.

**Q72. Is open-in-view enabled?**  
A: No — `spring.jpa.open-in-view: false`.

**Q73. What timezone does JDBC use?**  
A: UTC (`hibernate.jdbc.time_zone`).

---

## Observability & testing (10 questions)

**Q74. What actuator endpoints are exposed?**  
A: health, info, prometheus, metrics.

**Q75. What custom health check exists?**  
A: `RedisHealthIndicator`.

**Q76. Where does Prometheus scrape?**  
A: `docker/prometheus/prometheus.yml` — app:8081 and gateway:8080.

**Q77. Default Grafana credentials in Compose?**  
A: admin/admin.

**Q78. What integration test base class exists?**  
A: `AbstractIntegrationTest` with Testcontainers PostgreSQL + Redis.

**Q79. Name the integration tests.**  
A: `AuthFlowIT`, `UrlFlowIT`, `RateLimitIT`, `AdminAuthorizationIT`, `AnalyticsAndCacheIT`.

**Q80. Which modules have unit tests?**  
A: common, auth, url, rate-limit — not gateway, web, user, analytics, observability directly.

**Q81. How do ITs configure JWT secret?**  
A: Dynamic properties in `AbstractIntegrationTest`.

**Q82. What command runs all tests?**  
A: `mvn clean verify` (requires Docker for Testcontainers).

**Q83. Are actuator endpoints authenticated?**  
A: No — `SecurityConfig` permitAll — production risk.

---

## Docker & deployment (7 questions)

**Q84. What services are in docker-compose?**  
A: postgres, redis, linkflow-app, linkflow-gateway, prometheus, grafana.

**Q85. What Dockerfiles exist?**  
A: `docker/Dockerfile.app`, `docker/Dockerfile.gateway` — Temurin 21 multi-stage.

**Q86. What profile do Compose services use?**  
A: `SPRING_PROFILES_ACTIVE=prod`.

**Q87. Is TLS configured in the repo?**  
A: No — expected at load balancer/ingress.

**Q88. How should macOS gateway connect to app?**  
A: `LINKFLOW_APP_URI=http://127.0.0.1:8081` to avoid IPv6 issues.

**Q89. What env var is required for Docker app startup?**  
A: `LINKFLOW_JWT_SECRET` from `.env`.

**Q90. Does Dockerfile build the whole reactor?**  
A: `mvn -pl linkflow-app -am package` — includes feature modules, not web.

---

## Web UI (5 questions)

**Q91. What HTTP client does linkflow-web use?**  
A: Spring `RestClient` (not WebClient) — bean in `WebClientConfig`.

**Q92. What UI framework is used?**  
A: Tabler CSS via CDN + Thymeleaf templates.

**Q93. How is QR shown without exposing JWT to browser?**  
A: `/urls/{id}/qr-proxy` fetches PNG server-side.

**Q94. What is the only browser `fetch()` call?**  
A: Rate limit probe in `app.js` → `/tools/rate-limit/probe`.

**Q95. Session timeout?**  
A: 30 minutes; cookie `SameSite=strict`.

---

## Tradeoffs & follow-ups (10 questions)

**Q96. Biggest production security gap?**  
A: CORS wildcard default and Compose demo credentials — actuator/Swagger are now profile-gated.

**Q97. Why fail-open rate limiting for non-auth paths?**  
A: Prefer availability for redirects and authenticated API; auth paths fail closed to protect login/register.

**Q98. Why no click time-series rollup API?**  
A: Deliberate non-goal for v1 — aggregate counts plus recent click listing; no hourly/daily rollups or chart API.

**Q99. How would you scale horizontally?**  
A: Multiple app instances behind gateway; shared Postgres + Redis; web needs session store or sticky sessions.

**Q100. How would you split into microservices?**  
A: Start at natural ports (auth, url+redirect, analytics) — but would need distributed transactions or eventual consistency for current flows.

**Q101. What would you add first for production?**  
A: Secure actuator, secrets management, HTTPS, CORS lockdown, gateway tests.

**Q102. Why PostgreSQL over MongoDB for URLs?**  
A: Relational integrity (users, tokens, FKs), ACID transactions, Flyway migrations — see ADR-002.

**Q103. Why Redis if PostgreSQL could cache?**  
A: Sub-ms counters and TTL keys for rate limit and hot redirect path — see ADR-003.

**Q104. How does LinkFlow demonstrate idempotency?**  
A: `Idempotency-Key` header + `idempotency_records` replay for create endpoints.

**Q105. What features were planned but not built?**  
A: Admin user disable/delete, click event API, time-series charts — see feature matrix.

---

## Common follow-up questions

- **Draw the redirect sequence** — Use cache-aside → async analytics diagram from [system-design.md](system-design.md).
- **Draw the auth sequence** — Login → JWT + refresh token in DB.
- **Where would you add caching besides redirects?** — User profile read-heavy paths (not implemented today).
- **How do you test rate limiting?** — `RateLimitIT` with lowered RPM via test properties.
- **What happens if two users pick the same alias?** — DB unique constraint + Redis lock; `AliasCollisionException`.
- **Explain refresh rotation** — Old token revoked, new hash stored, reuse triggers revoke-all.

---

## Related documents

- [project-deep-dive.md](project-deep-dive.md)
- [system-design.md](system-design.md)
- [learning-roadmap.md](learning-roadmap.md)

# LinkFlow Code Walkthrough

Guided path from application startup through request handling. All class names verified in source.

---

## 1. Application startup (`linkflow-app`)

**Entry:** `com.linkflow.app.LinkFlowApplication`

```java
@SpringBootApplication(scanBasePackages = "com.linkflow")
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.linkflow")
@EntityScan(basePackages = "com.linkflow")
@EnableAsync
@EnableScheduling
```

**Startup sequence:**

1. Spring Boot auto-configuration loads datasources, Redis, JPA, security
2. Flyway runs migrations from `classpath:db/migration` (`application.yml`)
3. Beans from all `com.linkflow.*` modules register (controllers, services, filters)
4. `AdminBootstrap` (`ApplicationRunner`) optionally creates admin user
5. Server listens on port **8081**

**App-specific config beans** (`com.linkflow.app.config`):

- `WebMvcConfig` — CORS
- `AsyncConfig` — `clickTrackingExecutor` thread pool
- `OpenApiConfig` — Swagger metadata

---

## 2. Gateway startup (`linkflow-gateway`)

**Entry:** `com.linkflow.gateway.LinkFlowGatewayApplication`

**Routes** loaded from `application.yml`:

- `/api/**`, `/r/**`, `/swagger-ui/**`, `/v3/api-docs/**` → `${LINKFLOW_APP_URI}` (backend)
- `/css/**`, `/js/**`, `/webjars/**`, `/**` → `${LINKFLOW_WEB_URI}` (web UI)
- Gateway `/actuator/**` is served locally — **not** proxied to the app

**Filter:** `CorrelationIdGatewayFilter` — global, highest precedence; sets/propagates `X-Correlation-ID`.

Port **8080**.

---

## 3. Security filter chain (API request)

**Config:** `com.linkflow.auth.infrastructure.security.SecurityConfig`

Order for authenticated API requests:

```
HTTP Request
  → JwtAuthenticationFilter
  → RateLimitFilter (if bean present)
  → DispatcherServlet
  → Controller
```

### JwtAuthenticationFilter

1. Read `Authorization` header
2. Strip `Bearer ` prefix
3. `JwtService.validateToken` / parse claims
4. Build `UserPrincipal` → `SecurityContextHolder`

### RateLimitFilter

1. Skip if path starts with `/actuator`, `/swagger-ui`, `/v3/api-docs`
2. Resolve user ID or client IP
3. `RateLimitService.checkForUser` or `checkForIp` (Redis Lua `rate_limiter.lua` using a sliding-window sorted set)
4. Set rate limit headers; throw `RateLimitExceededException` if denied (HTTP 429) or `ServiceUnavailableException` if Redis is down on auth paths (HTTP 503)

---

## 4. Example: Login flow

```
POST /api/v1/auth/login
```

| Step | Class | Method |
|------|-------|--------|
| 1 | `AuthController` | `login(@Valid LoginRequest)` |
| 2 | `AuthService` | `login` — `UserLookupPort.findByEmail` |
| 3 | `PasswordEncoder` | `matches(raw, passwordHash)` |
| 4 | `JwtService` | `generateAccessToken(UserPrincipal)` |
| 5 | `RefreshTokenService` | `createRefreshToken(userId)` |
| 6 | Response | `ApiResponse.of(TokenResponse)` |

**Port adapter:** `UserLookupAdapter` (`linkflow-user`) implements `UserLookupPort`.

---

## 5. Example: Create short URL

```
POST /api/v1/urls
Authorization: Bearer ...
Idempotency-Key: optional
```

| Step | Class | Method |
|------|-------|--------|
| 1 | `UrlController` | `createUrl` |
| 2 | `UrlService` | `createUrl` — check idempotency cache |
| 3 | `IdempotencyService` | `findCached` / `store` |
| 4 | `UrlService` | `persistUrl` — validate URL, alias lock |
| 5 | `RedisLockService` | `tryLock("alias:...")` for custom alias |
| 6 | `ShortCodeGenerator` | generate if no alias |
| 7 | `ShortUrlRepository` | `save(ShortUrl)` |
| 8 | Response | `UrlResponse` with `linkflow.base-url + /r/ + code` |

---

## 6. Example: Redirect flow

```
GET /r/{shortCode}
```

| Step | Class | Method | Detail |
|------|-------|--------|--------|
| 1 | `RedirectController` | `redirect(@PathVariable shortCode)` | Entry point for HTTP redirection |
| 2 | `RedirectService` | `resolveRedirect(shortCode, request)` | Core redirect logic orchestration |
| 3 | `UrlCacheService` | `get(url:shortcode:{lower})` | Check Redis cache for cached entry |
| 4a | cache hit (fresh) | return URL | Serve directly from cache; execute click tracking asynchronously |
| 4b | cache hit (negative) | throw exception | If entry has `negative=true` flag, immediately throw `ResourceNotFoundException` |
| 4c | cache hit (stale) | serve + refresh | If entry is stale (SWR), serve it and trigger `triggerAsyncRefresh` in background thread |
| 4d | cache miss | check stampede lock | Attempt to acquire `cache_refresh:{code}` lock via `RedisLockService` |
| 5a | lock acquired | fetch DB + write cache | Read from `ShortUrlRepository`, write positive or negative entry to cache, release lock |
| 5b | lock failed | wait + retry | Sleep 100ms and retry `UrlCacheService.get` up to 3 times. If still missing, query DB directly. |
| 6 | `ClickTrackingPort` | `trackClick(command)` | Delegate tracking command asynchronously |
| 7 | `ClickTrackingAdapter` | delegates to `ClickTrackingService` | Connects URL module to Analytics module |
| 8 | `ClickTrackingService` | `@Async trackClick` | Buffers event to `analytics:clicks:stream`, increments counter Hash, tracks URL in Set |
| 9 | `RedirectController` | return redirect | `ResponseEntity.status(302).location(uri)` returned to user |

---

## 7. Database interactions

**Repositories** (Spring Data JPA):

- Auth: `RefreshTokenRepository`
- User: `UserRepository`, `RoleRepository`
- URL: `ShortUrlRepository`, `IdempotencyRecordRepository`
- Analytics: `ClickEventRepository`, `UrlAnalyticsRepository`, `StatsRepository`

**Auditing:** `AuditableEntity` + `@EnableJpaAuditing` populates `createdAt`, `updatedAt`, etc.

**Transactions:** `@Transactional` on service methods; read-only for queries.

---

## 8. Redis interactions

| Service | Key / Pattern | Details |
|---------|---------------|---------|
| `UrlCacheService` | `url:shortcode:{code}` | Redirect cache-aside. Positive TTL is 15m (30m extended for SWR). Negative TTL is 90s. Evicted on update/delete/create. |
| `RateLimitService` | `rate_limit:user:{userId}` / `rate_limit:ip:{ip}` | Sorted sets tracking request timestamps in microseconds. Checked atomically via `rate_limiter.lua`. |
| `RedisLockService` | `lock:alias:{name}` / `lock:cache_refresh:{code}` | Mutual exclusion locks. Acquire via `SET NX EX`; release via `unlock.lua` atomic check. |
| `ClickTrackingService` | `analytics:clicks:stream` / `analytics:counter:{id}` / `analytics:active_urls` | Buffers click events in a Redis Stream and counters in a Redis Hash. Tracks active counter IDs in a Redis Set. |

**Config:** `RedisConfig` in `linkflow-common` — shared templates.

**Health:** `RedisHealthIndicator` — PING check in actuator health.

---

## 9. Analytics processing

**Write (async buffer):**

```
RedirectService.trackClick
  → ClickTrackingPort
  → ClickTrackingService.trackClick (@Async clickTrackingExecutor)
  → Buffer to Redis Stream (analytics:clicks:stream)
  → Increment Redis Hash counter (analytics:counter:{id})
  → Add ID to Redis Set (analytics:active_urls)
```

**Flush (scheduled daemon):**

```
AnalyticsFlushService.flush (Every 30s / PreDestroy)
  → Read Stream (XREADGROUP) → saveAll to click_events (PostgreSQL) → XACK & XTRIM
  → Read Set (active_urls) → get Hash count → save to url_analytics (PostgreSQL) → delete Hash/Set key
```

**Read:**

```
AnalyticsController
  → AnalyticsQueryService.getUrlAnalytics / getTopUrlsForCurrentUser
  → UrlAnalyticsRepository / StatsRepository (reads PostgreSQL tables populated by flush job)
```

---

## 10. Web UI request path

**Entry:** `com.linkflow.web.LinkFlowWebApplication` (port 8082)

Example: dashboard load

| Step | Class | Detail |
|------|-------|--------|
| 1 | `SessionAuthFilter` | Load `AuthState` from session |
| 2 | `WebSecurityConfig` | Authorize `/dashboard` |
| 3 | `DashboardController` | handler method |
| 4 | `ApiCallHelper` | `requireAuth()` + wrap with refresh |
| 5 | `UrlApiClient` / `AnalyticsApiClient` | `RestClient` → gateway |
| 6 | `BackendClient` | Adds `Authorization: Bearer` |
| 7 | Gateway → App | Normal API flow |
| 8 | Controller | Returns Thymeleaf view name + Model |

**Config:** `WebClientConfig` — `RestClient` bean with `linkflow.web.gateway-url`.

---

## 11. Scheduled jobs

### ExpiredUrlCleanupJob

- Cron: `0 0 * * * *` (every hour)
- Calls `UrlService.deactivateExpiredUrls()` and `cleanupExpiredIdempotencyRecords()`

### AnalyticsFlushService

- Fixed delay: `${linkflow.analytics.flush-interval-ms:30000}` (every 30 seconds)
- Calls `flushClickEvents()` and `flushCounters()` to sync buffered Redis data to PostgreSQL.
- Also runs on application shutdown (`@PreDestroy` block) to ensure zero data loss.

---

## 12. Exception handling

**Global handler:** `com.linkflow.common.exception.GlobalExceptionHandler` (in common module)

Maps domain exceptions to HTTP status + `ApiErrorResponse`:

- `ResourceNotFoundException` → 404
- `RateLimitExceededException` → 429
- `ConflictException` → 409
- `AccessDeniedException` → 403

Web module: `com.linkflow.web.config.GlobalExceptionHandler` — redirects for session expiry.

---

## Related documents

- [system-design.md](system-design.md) — diagrams and architecture
- [project-deep-dive.md](project-deep-dive.md) — subsystem narrative
- [api-inventory.md](api-inventory.md) — endpoint reference

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

- `/api/**`, `/r/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**` → `${LINKFLOW_APP_URI}`

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
3. `RateLimitService.checkForUser` or `checkForIp` (Redis Lua)
4. Set rate limit headers; throw `RateLimitExceededException` if denied

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

| Step | Class | Method |
|------|-------|--------|
| 1 | `RedirectController` | `redirect(@PathVariable shortCode)` |
| 2 | `RedirectService` | `resolveRedirect(shortCode, request)` |
| 3 | `UrlCacheService` | `get(url:shortcode:{lower})` |
| 4a | cache hit | validate + track + return URL |
| 4b | cache miss | `ShortUrlRepository.findByShortCode` |
| 5 | `UrlCacheService` | `put(shortUrl)` — 15 min TTL |
| 6 | `ClickTrackingPort` | `trackClick(command)` |
| 7 | `ClickTrackingAdapter` | delegates to `ClickTrackingService` |
| 8 | `ClickTrackingService` | `@Async trackClick` — save event + increment analytics |
| 9 | `RedirectController` | `ResponseEntity.status(302).location(uri)` |

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

| Service | When |
|---------|------|
| `UrlCacheService` | Redirect path; evict on URL update/delete |
| `RateLimitService` | Every non-excluded API request |
| `RedisLockService` | Custom alias creation |

**Config:** `RedisConfig` in `linkflow-common` — shared templates.

**Health:** `RedisHealthIndicator` — PING check in actuator health.

---

## 9. Analytics processing

**Write (async):**

```
RedirectService.trackClick
  → ClickTrackingPort
  → ClickTrackingService.trackClick (@Async clickTrackingExecutor)
  → ClickEventRepository.save
  → UrlAnalyticsRepository save/increment
```

**Read:**

```
AnalyticsController
  → AnalyticsQueryService.getUrlAnalytics / getTopUrlsForCurrentUser
  → UrlAnalyticsRepository / StatsRepository
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

**Class:** `com.linkflow.app.scheduler.ExpiredUrlCleanupJob`

- Cron: `0 0 * * * *` (every hour)
- Calls `UrlService.deactivateExpiredUrls()` and `cleanupExpiredIdempotencyRecords()`

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

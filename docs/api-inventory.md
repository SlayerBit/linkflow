# LinkFlow API Inventory

**Canonical API reference.** Base URL via gateway: `http://localhost:8080`. Direct app access: `http://localhost:8081` (same paths).

All JSON success responses use `com.linkflow.common.api.ApiResponse`:

```json
{
  "success": true,
  "timestamp": "2026-06-12T10:00:00Z",
  "correlationId": "uuid",
  "data": { }
}
```

Errors use `ApiErrorResponse` with `errorCode`, `message`, optional `details`. Interactive docs: `/swagger-ui/index.html` (via gateway).

---

## Authentication domain

Controller: `com.linkflow.auth.api.controller.AuthController`  
Base path: `/api/v1/auth`  
Security: **Public** (no Bearer token)

### POST `/api/v1/auth/register`

| Field | Value |
|-------|-------|
| Request DTO | `RegisterRequest` |
| Response DTO | `RegisterResponse` (HTTP 201) |
| Rate limiting | Per-IP (`linkflow.rate-limit.ip-rpm`, default 200/min) |

**Request validation (`RegisterRequest`):**

| Field | Rules |
|-------|-------|
| `email` | `@NotBlank`, `@Email` |
| `password` | 8–128 chars; must include upper, lower, digit, special (`@$!%*?&`) |
| `firstName` | `@NotBlank`, max 100 |
| `lastName` | max 100 |

**Response fields:** `id`, `email`, `firstName`, `lastName`, `roles`, `createdAt`

**Business purpose:** Create user with `ROLE_USER` via `AuthService.register` → `UserLookupPort.createUser`.

**Errors:** `409 Conflict` if email exists (`ConflictException`).

---

### POST `/api/v1/auth/login`

| Field | Value |
|-------|-------|
| Request DTO | `LoginRequest` |
| Response DTO | `TokenResponse` |
| Rate limiting | Per-IP |

**Request validation:**

| Field | Rules |
|-------|-------|
| `email` | `@NotBlank`, `@Email` |
| `password` | `@NotBlank` |

**Response fields:** `accessToken`, `refreshToken`, `tokenType` (`Bearer`), `expiresIn` (seconds)

**Business purpose:** Validate credentials, issue JWT access token (`JwtService`) and opaque refresh token (`RefreshTokenService`).

**Errors:** `401` invalid credentials (`InvalidCredentialsException`).

---

### POST `/api/v1/auth/refresh`

| Field | Value |
|-------|-------|
| Request DTO | `RefreshTokenRequest` — `{ "refreshToken": "..." }` |
| Response DTO | `TokenResponse` |
| Rate limiting | Per-IP |

**Business purpose:** Rotate refresh token; issue new access + refresh pair (`RefreshTokenService.rotateRefreshToken`).

**Errors:** `401` expired/revoked/unknown token.

---

### POST `/api/v1/auth/logout`

| Field | Value |
|-------|-------|
| Request DTO | `LogoutRequest` — `{ "refreshToken": "..." }` |
| Response DTO | `ApiResponse<Map>` — `{ "message": "Logged out successfully" }` |
| Authentication | Bearer access token required |
| Rate limiting | Per-user |

**Business purpose:** Revoke refresh token in database.

---

## User domain

### GET `/api/v1/users/me`

| Field | Value |
|-------|-------|
| Controller | `com.linkflow.user.api.controller.UserController` |
| Response DTO | `UserResponse` |
| Authentication | Bearer (any authenticated user) |
| Rate limiting | Per-user |

**Response fields:** `id`, `email`, `firstName`, `lastName`, `roles`, `createdAt`, `updatedAt`

---

### PUT `/api/v1/users/me`

| Field | Value |
|-------|-------|
| Request DTO | `UpdateProfileRequest` — `firstName`, `lastName` (optional strings) |
| Response DTO | `UserResponse` |
| Authentication | Bearer |
| Rate limiting | Per-user |

**Business purpose:** Update profile fields (email not mutable via this endpoint).

---

### GET `/api/v1/admin/users`

| Field | Value |
|-------|-------|
| Controller | `com.linkflow.user.api.controller.AdminUserController` |
| Response DTO | `PagedResponse<UserResponse>` |
| Authentication | `ROLE_ADMIN` (`@PreAuthorize`) |
| Query params | `page` (default 0), `size` (default 20, max 100), `sortBy`, `direction` |

---

### GET `/api/v1/admin/users/{id}`

| Field | Value |
|-------|-------|
| Response DTO | `UserResponse` (includes `enabled`) |
| Authentication | `ROLE_ADMIN` |
| Path param | `id` — UUID |

---

### PATCH `/api/v1/admin/users/{id}/disable`

| Field | Value |
|-------|-------|
| Response DTO | `UserResponse` with `enabled: false` |
| Authentication | `ROLE_ADMIN` |
| Business purpose | Prevents login and token refresh for the user |

---

### PATCH `/api/v1/admin/users/{id}/enable`

| Field | Value |
|-------|-------|
| Response DTO | `UserResponse` with `enabled: true` |
| Authentication | `ROLE_ADMIN` |
| Business purpose | Re-enables a previously disabled account |

---

### DELETE `/api/v1/admin/users/{id}`

| Field | Value |
|-------|-------|
| Response DTO | `UserResponse` |
| Authentication | `ROLE_ADMIN` |
| Business purpose | Soft-delete (`User.softDelete()` — sets `deleted=true`, `enabled=false`) |

---

## URL domain

Controller: `com.linkflow.url.api.controller.UrlController`  
Base path: `/api/v1/urls`  
Authentication: Bearer (all endpoints)

### POST `/api/v1/urls`

| Field | Value |
|-------|-------|
| Request DTO | `CreateUrlRequest` |
| Response DTO | `UrlResponse` (HTTP 201) |
| Header | `Idempotency-Key` (optional) |
| Rate limiting | Per-user |

**Request validation:**

| Field | Rules |
|-------|-------|
| `originalUrl` | `@NotBlank`, max 2048 |
| `customAlias` | max 100; pattern `^[a-zA-Z0-9_-]+$` or empty |
| `expiresAt` | optional `Instant` (must be future — validated in service) |

**Response fields:** `id`, `shortCode`, `shortUrl`, `originalUrl`, `expiresAt`, `active`, `createdAt`

**Business purpose:** Create short URL; optional idempotency replay from `idempotency_records`.

---

### POST `/api/v1/urls/bulk`

| Field | Value |
|-------|-------|
| Request DTO | `BulkCreateUrlRequest` — `{ "urls": [ CreateUrlRequest, ... ] }` |
| Response DTO | `BulkCreateUrlResponse` — `{ "urls": [...], "count": N }` |
| Header | `Idempotency-Key` (**required**) |
| Validation | `@NotEmpty` on `urls` list; max 100 URLs (service validation) |

---

### GET `/api/v1/urls`

| Field | Value |
|-------|-------|
| Response DTO | `PagedResponse<UrlResponse>` |
| Query params | `page`, `size` (max 100), `sortBy` (default `createdAt`), `direction` |

**Business purpose:** List current user's non-deleted URLs.

---

### GET `/api/v1/urls/{id}`

| Field | Value |
|-------|-------|
| Response DTO | `UrlResponse` |
| Authorization | Owner only (`UrlService.findOwnedUrl`) |

---

### PATCH `/api/v1/urls/{id}`

| Field | Value |
|-------|-------|
| Request DTO | `UpdateUrlRequest` — optional `expiresAt`, `active` |
| Response DTO | `UrlResponse` |
| Authorization | Owner only |

**Business purpose:** Update expiry or active flag; evicts Redis cache on change.

---

### DELETE `/api/v1/urls/{id}`

| Field | Value |
|-------|-------|
| Response DTO | `ApiResponse<Void>` (empty data) |
| Authorization | Owner only |

**Business purpose:** Soft-delete (`ShortUrl.softDelete()`).

---

### GET `/api/v1/urls/{id}/qr`

| Field | Value |
|-------|-------|
| Response | `image/png` bytes (`QrCodeService` / ZXing) |
| Authorization | Owner only |

---

## Redirect domain

Controller: `com.linkflow.url.api.controller.RedirectController`  
Base path: `/r`

### GET `/r/{shortCode}`

| Field | Value |
|-------|-------|
| Authentication | **None** (public) |
| Response | HTTP **302** redirect to `originalUrl` |
| Rate limiting | Per-IP |
| Service | `RedirectService.resolveRedirect` |

**Business purpose:** Public short link resolution with cache-aside and async click tracking.

**Errors:** 404 not found, deactivated, expired (domain exceptions).

---

## Admin URL domain

Controller: `com.linkflow.url.api.controller.AdminUrlController`  
Base path: `/api/v1/admin/urls`  
Authentication: `ROLE_ADMIN`

### GET `/api/v1/admin/urls`

Paged list of all URLs (`PagedResponse<UrlResponse>`).

### PATCH `/api/v1/admin/urls/{id}/deactivate`

Force-deactivate URL (admin override).

### GET `/api/v1/admin/urls/{id}/qr`

| Field | Value |
|-------|-------|
| Response | `image/png` (raw bytes, not `ApiResponse`) |
| Authentication | Bearer |
| Authorization | `ROLE_ADMIN` |

Returns QR code PNG for any URL regardless of owner.

---

## Analytics domain

Controllers: `AnalyticsController`, `AdminAnalyticsController`

### GET `/api/v1/urls/{id}/analytics`

| Field | Value |
|-------|-------|
| Response DTO | `UrlAnalyticsResponse` |
| Authentication | Bearer |
| Authorization | Owner only (`AnalyticsQueryService`) |

**Response fields:** `shortUrlId`, `shortCode`, `totalClicks`, `lastAccessedAt`

---

### GET `/api/v1/urls/{id}/analytics/clicks`

| Field | Value |
|-------|-------|
| Response DTO | `List<ClickEventResponse>` |
| Query param | `limit` (default 20, min 1, max 100) |
| Authentication | Bearer |
| Authorization | Owner only |

**Response item fields:** `id`, `shortUrlId`, `shortCode`, `clickedAt`, `ipAddress` (masked for users), `userAgent`, `referer`

**Business purpose:** Recent raw click events for a specific URL (newest first).

---

### GET `/api/v1/urls/{id}/analytics/click-trend`

| Field | Value |
|-------|-------|
| Response DTO | `List<ClickTrendResponse>` |
| Query param | `days` (7, 30, or 90; default 30) |
| Authentication | Bearer |
| Authorization | Owner only |

**Response item fields:** `date` (YYYY-MM-DD), `clicks`

**Business purpose:** Click counts aggregated daily over the specified range (7, 30, or 90 days) for a specific URL.

---

### GET `/api/v1/analytics/recent-clicks`

| Field | Value |
|-------|-------|
| Response DTO | `List<ClickEventResponse>` |
| Query param | `limit` (default 10, min 1, max 100) |
| Authentication | Bearer |

**Response item fields:** `id`, `shortUrlId`, `shortCode`, `clickedAt`, `ipAddress` (masked for users), `userAgent`, `referer`

**Business purpose:** Latest click events across all URLs owned by the current authenticated user.

---

### GET `/api/v1/analytics/top`

| Field | Value |
|-------|-------|
| Response DTO | `List<TopUrlResponse>` |
| Query param | `limit` (default 10, min 1, max 100) |
| Authentication | Bearer |

**Response item fields:** `shortUrlId`, `shortCode`, `totalClicks`

**Business purpose:** Current user's top URLs by click count.

---

### GET `/api/v1/admin/analytics/top`

| Field | Value |
|-------|-------|
| Authentication | `ROLE_ADMIN` |
| Response DTO | `List<TopUrlResponse>` (system-wide) |

---

### GET `/api/v1/admin/analytics/stats`

| Field | Value |
|-------|-------|
| Authentication | `ROLE_ADMIN` |
| Response DTO | `SystemStatsResponse` |

**Fields:** `totalUsers`, `totalUrls`, `totalClicks`, `activeUrls`, `expiredUrls`, `deletedUrls`

---

### GET `/api/v1/admin/analytics/urls/{id}/clicks`

| Field | Value |
|-------|-------|
| Authentication | `ROLE_ADMIN` |
| Response DTO | `List<ClickEventResponse>` |
| Query param | `limit` (default 20, max 100) |

**Response item fields:** `id`, `shortUrlId`, `shortCode`, `clickedAt`, `ipAddress` (unmasked raw IP), `userAgent`, `referer`

---

### GET `/api/v1/admin/analytics/urls/{id}/click-trend`

| Field | Value |
|-------|-------|
| Authentication | `ROLE_ADMIN` |
| Response DTO | `List<ClickTrendResponse>` |
| Query param | `days` (7, 30, or 90; default 30) |

**Response item fields:** `date` (YYYY-MM-DD), `clicks`

**Business purpose:** Daily aggregated clicks over the specified range (7, 30, or 90 days) for any specific URL.

---

### GET `/api/v1/admin/analytics/click-trend`

| Field | Value |
|-------|-------|
| Authentication | `ROLE_ADMIN` |
| Response DTO | `List<ClickTrendResponse>` |
| Query param | `days` (7, 30, or 90; default 30) |

**Response item fields:** `date` (YYYY-MM-DD), `clicks`

**Business purpose:** Daily aggregated clicks system-wide across all users over the specified range (7, 30, or 90 days).

---

### GET `/api/v1/admin/analytics/recent-clicks`

| Field | Value |
|-------|-------|
| Authentication | `ROLE_ADMIN` |
| Response DTO | `List<ClickEventResponse>` |
| Query param | `limit` (default 10, min 1, max 100) |

**Response item fields:** `id`, `shortUrlId`, `shortCode`, `clickedAt`, `ipAddress` (unmasked raw IP), `userAgent`, `referer`

**Business purpose:** Latest click events system-wide across all users and short URLs.

---

## Actuator endpoints

| Process | Path | Prod exposure |
|---------|------|---------------|
| Backend app :8081 | `/actuator/health` | Public |
| Backend app :8081 | `/actuator/prometheus`, `/actuator/metrics/**` | Public only if `LINKFLOW_METRICS_PUBLIC=true` |
| Backend app :8081 | Other actuator paths | Denied |
| Gateway :8080 | `/actuator/health` | Public (not proxied to backend) |

Swagger/OpenAPI: disabled and denied in `prod` profile.

---

## Rate limiting behavior (all API endpoints)

Applied by `RateLimitFilter` except `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`.

| Context | Key | Default limit |
|---------|-----|---------------|
| Authenticated | `rate_limit:user:{userId}:{minuteEpoch}` | 100 RPM |
| Anonymous | `rate_limit:ip:{ip}:{minuteEpoch}` | 200 RPM |

**Response headers:** `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`

**Exceeded:** HTTP 429, `RateLimitExceededException`

**Redis unavailable:**

| Path category | Behavior |
|---------------|----------|
| `/api/v1/auth/**` | HTTP 503, `RATE_LIMIT_BACKEND_UNAVAILABLE` (fail-closed) |
| All other filtered paths | Fail-open (request allowed, logged) |

---

## Web UI routes (linkflow-web :8082)

Not part of the backend API. Documented for completeness — server-rendered HTML unless noted.

| Method | Path | Controller | Purpose |
|--------|------|------------|---------|
| GET | `/`, `/login`, `/register` | `PublicController` | Public pages |
| POST | `/login`, `/register`, `/logout` | `AuthController` | Auth forms |
| GET | `/dashboard` | `DashboardController` | User dashboard |
| GET/POST | `/urls/**` | `UrlController` | URL management UI |
| GET/POST | `/profile` | `ProfileController` | Profile |
| GET/POST | `/admin/**` | `AdminController` | Admin UI |
| GET | `/tools/**` | `ToolsController` | Rate limit demo, health |
| GET | `/tools/rate-limit/probe` | `ToolsController` | JSON probe (`@ResponseBody`) |

Web calls backend exclusively through `LINKFLOW_GATEWAY_URL` via `RestClient` clients in `com.linkflow.web.client`.

---

## Related documents

- [system-design.md](system-design.md) — architecture and flows
- [api.md](api.md) — quick reference table (secondary)
- [security-review.md](security-review.md) — auth requirements detail

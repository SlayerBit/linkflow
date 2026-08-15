# LinkFlow API

REST inventory generated against the controllers in this repository. Interactive OpenAPI is available only under the `dev` profile: `http://localhost:8080/swagger-ui/index.html` (gateway) or `http://localhost:8081/swagger-ui.html` (app direct).

**Base URL**

| How you run | Base |
|-------------|------|
| Compose (Nginx) | `https://localhost` |
| Gateway on the host | `http://localhost:8080` |
| App direct | `http://localhost:8081` |

Paths are the same in all three cases. There are **45** JSON/PNG/redirect endpoints on the backend plus the SSR web routes listed at the end.

## Envelopes

Success (`com.linkflow.common.api.ApiResponse`):

```json
{
  "success": true,
  "timestamp": "2026-08-16T10:00:00Z",
  "correlationId": "uuid",
  "data": { }
}
```

Errors (`ApiErrorResponse`): `errorCode`, `message`, optional `details`. Correlation ID is also present.

PNG QR responses and `GET /r/{shortCode}` (302) are not wrapped.

## Authentication

All `/api/**` except the public auth and redirect paths require `Authorization: Bearer <accessToken>`. Admin routes additionally require `ROLE_ADMIN` (`@PreAuthorize`).

Web UI sessions are not this API: the browser talks HTML forms to `linkflow-web`, which stores JWTs in a Redis `HttpSession` and calls these endpoints via `RestClient`. Tokens never reach JavaScript.

## Rate limiting

Applied by `RateLimitFilter` except `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`.

| Context | Key | Default |
|---------|-----|---------|
| Authenticated | per user | 100 RPM |
| Anonymous | per IP | 200 RPM |

Headers on filtered responses: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.

Exceeded → **429**. Redis down:

| Path | Behaviour |
|------|-----------|
| `/api/v1/auth/**` | **503** fail-closed (`RATE_LIMIT_BACKEND_UNAVAILABLE`) |
| Everything else | Fail-open (allowed, logged) |

Nginx adds a coarser edge limit in Compose (~10/min on credential paths, ~100/s generally). See [DEPLOYMENT.md](DEPLOYMENT.md).

---

## Authentication domain

Controller: `com.linkflow.auth.api.controller.AuthController`  
Base: `/api/v1/auth`

### POST `/api/v1/auth/register`

Public. Rate limit: per-IP. **201** `RegisterResponse`.

| Field | Rules |
|-------|-------|
| `email` | `@NotBlank`, `@Email` |
| `password` | 8–128 chars; upper, lower, digit, special (`@$!%*?&`) |
| `firstName` | `@NotBlank`, max 100 |
| `lastName` | max 100 |

Creates `ROLE_USER`, emails a 24h activation link (unless verification is disabled). **409** if the address exists.

Response: `id`, `email`, `firstName`, `lastName`, `roles`, `createdAt`.

### POST `/api/v1/auth/login`

Public. Per-IP. **200** `TokenResponse`: `accessToken`, `refreshToken`, `tokenType` (`Bearer`), `expiresIn` (seconds).

| Field | Rules |
|-------|-------|
| `email` | `@NotBlank`, `@Email` |
| `password` | `@NotBlank` |

**401** invalid credentials. **401** `EMAIL_NOT_VERIFIED` when the account exists but is not activated — distinct so a client can offer resend rather than claiming the password was wrong.

### POST `/api/v1/auth/refresh`

Public. Per-IP. Body: `{ "refreshToken": "..." }`. Rotates: presented token is revoked, a replacement pair is issued. **401** expired/revoked/unknown. Reuse of a revoked token revokes **all** of that user's refresh tokens.

### POST `/api/v1/auth/logout`

Public (`SecurityConfig` permits it without a Bearer token). Per-IP rate limit. Body: `{ "refreshToken": "..." }`. Revokes that refresh token.

### POST `/api/v1/auth/change-password`

Bearer. Per-user. Requires `currentPassword` and `newPassword` (8–128). All sessions revoked on success.

### POST `/api/v1/auth/verify-email`

Public. Per-IP. Body: `{ "token": "..." }`.

Idempotent once the account is verified (mail scanners prefetch). **404** unknown token. **409** expired or superseded before activation.

### POST `/api/v1/auth/resend-verification`

Public. Per-IP. Body: `{ "email": "..." }`.

Always **200** with a non-committal message — registered, already verified, or in cooldown are indistinguishable. Per-recipient mail cooldown applies; a suppressed request leaves the previous link working.

### POST `/api/v1/auth/forgot-password`

Public. Per-IP. Body: `{ "email": "..." }`. Emails a 15-minute reset link. Always **200**. Same cooldown semantics as resend.

### POST `/api/v1/auth/reset-password`

Public. Per-IP. Body: `{ "token", "newPassword" }`.

**Not** idempotent. Consumes the token, sets the password, revokes every session. **404** unknown. **409** spent, expired, or superseded.

---

## User domain

Controller: `com.linkflow.user.api.controller.UserController`  
Base: `/api/v1/users`  
Auth: Bearer

### GET `/api/v1/users/me`

`UserResponse`: `id`, `email`, `firstName`, `lastName`, `roles`, `createdAt`, `updatedAt`.

### PUT `/api/v1/users/me`

`UpdateProfileRequest`: optional `firstName`, `lastName`. Email is not mutable here — use email-change.

### POST `/api/v1/users/me/email-change-request`

`EmailChangeRequestDto`: `currentPassword`, `newEmail`.

Sends a 24h confirmation link to the **new** address. The current address stays usable until the link is opened. Current password is required even though the caller is authenticated — a stolen session must not be enough to steal the mailbox.

**409** wrong password, unchanged address, or address already taken. **429** if a confirmation was just sent to that address.

### POST `/api/v1/users/verify-email-change`

Body: `{ "token": "..." }`. Moves the account, revokes every session. Idempotent once the account already carries the new address. **404** unknown. **409** superseded, expired, or claimed by another account.

---

## Admin users

Controller: `com.linkflow.user.api.controller.AdminUserController`  
Base: `/api/v1/admin/users`  
Auth: `ROLE_ADMIN`

### GET `/api/v1/admin/users`

`PagedResponse<UserResponse>`. Query: `page` (0), `size` (20, max 100), `sortBy`, `direction`.

### GET `/api/v1/admin/users/{id}`

`UserResponse` including `enabled`. Path `id` is UUID.

### PATCH `/api/v1/admin/users/{id}/disable`

Prevents login and token refresh.

### PATCH `/api/v1/admin/users/{id}/enable`

Re-enables a disabled account.

### DELETE `/api/v1/admin/users/{id}`

Soft-delete (`deleted=true`, `enabled=false`).

### PATCH `/api/v1/admin/users/{id}/roles`

`UpdateRolesRequest`: `{ "roles": ["USER"] }` or `["USER","ADMIN"]` (`@NotEmpty`). JWT roles stay stale until the user refreshes or logs in again.

---

## URL domain

Controller: `com.linkflow.url.api.controller.UrlController`  
Base: `/api/v1/urls`  
Auth: Bearer (owner)

### POST `/api/v1/urls`

**201** `UrlResponse`. Optional header `Idempotency-Key`.

| Field | Rules |
|-------|-------|
| `originalUrl` | `@NotBlank`, max 2048; SSRF-checked |
| `customAlias` | max 100; `^[a-zA-Z0-9_-]+$` or empty |
| `expiresAt` | optional future `Instant` |

Response: `id`, `shortCode`, `shortUrl`, `originalUrl`, `expiresAt`, `active`, `createdAt`.

### POST `/api/v1/urls/bulk`

`BulkCreateUrlRequest`: `{ "urls": [ CreateUrlRequest, ... ] }`. Header `Idempotency-Key` **required**. Max 100 URLs. Response: `{ "urls": [...], "count": N }`.

### GET `/api/v1/urls`

Current user's non-deleted URLs. `PagedResponse<UrlResponse>`. Query: `page`, `size` (max 100), `sortBy` (default `createdAt`), `direction`.

### GET `/api/v1/urls/{id}`

Owner only.

### PATCH `/api/v1/urls/{id}`

`UpdateUrlRequest`: optional `expiresAt`, `active`. Evicts Redis cache.

### DELETE `/api/v1/urls/{id}`

Soft-delete. Evicts cache.

### PATCH `/api/v1/urls/{id}/reactivate`

Owner reactivates a deactivated URL.

### GET `/api/v1/urls/{id}/qr`

`image/png` (ZXing). Owner only. Not an `ApiResponse`.

Idempotency records are keyed `(user_id, endpoint, idempotency_key)` with a request-body hash (Flyway V8) so a reused key with a different body is rejected. 24h TTL; hourly cleanup.

---

## Redirect

Controller: `com.linkflow.url.api.controller.RedirectController`

### GET `/r/{shortCode}`

Public. Per-IP. HTTP **302** to `originalUrl`. Redis cache-aside (see [ARCHITECTURE.md](ARCHITECTURE.md)). Async click tracking. **404** missing, deactivated, or expired.

Nginx sets `Cache-Control: no-store` on this path.

---

## Admin URLs

Controller: `com.linkflow.url.api.controller.AdminUrlController`  
Base: `/api/v1/admin/urls`  
Auth: `ROLE_ADMIN`

### GET `/api/v1/admin/urls`

Paged list of all URLs.

### GET `/api/v1/admin/urls/{id}`

Any URL regardless of owner.

### PATCH `/api/v1/admin/urls/{id}/deactivate`

Admin override.

### PATCH `/api/v1/admin/urls/{id}/reactivate`

Admin override.

### GET `/api/v1/admin/urls/{id}/qr`

`image/png` for any URL.

---

## Analytics (owner)

Controller: `com.linkflow.analytics.api.controller.AnalyticsController`

User click feeds **mask** IP addresses.

### GET `/api/v1/urls/{id}/analytics`

Owner. `UrlAnalyticsResponse`: `shortUrlId`, `shortCode`, `totalClicks`, `lastAccessedAt`.

### GET `/api/v1/urls/{id}/analytics/clicks`

Owner. `List<ClickEventResponse>`. Query `limit` (default 20, 1–100). Fields: `id`, `shortUrlId`, `shortCode`, `clickedAt`, `ipAddress` (masked), `userAgent`, `referer`.

### GET `/api/v1/urls/{id}/analytics/click-trend`

Owner. `List<ClickTrendResponse>` (`date`, `clicks`). Query `days`: 7, 30, or 90 (default 30).

### GET `/api/v1/analytics/top`

Bearer. Current user's top URLs. Query `limit` (default 10, 1–100). `TopUrlResponse`: `shortUrlId`, `shortCode`, `totalClicks`.

### GET `/api/v1/analytics/recent-clicks`

Bearer. Latest clicks across URLs the caller owns. Query `limit` (default 10, 1–100).

---

## Admin analytics

Controller: `com.linkflow.analytics.api.controller.AdminAnalyticsController`  
Base: `/api/v1/admin/analytics`  
Auth: `ROLE_ADMIN`

Admin click feeds return **unmasked** IPs.

### GET `/api/v1/admin/analytics/top`

System-wide `List<TopUrlResponse>`. Query `limit`.

### GET `/api/v1/admin/analytics/stats`

`SystemStatsResponse`: `totalUsers`, `totalUrls`, `totalClicks`, `activeUrls`, `inactiveUrls`, `expiredUrls`, `deletedUrls`.

### GET `/api/v1/admin/analytics/urls/{id}`

Analytics for any URL (`UrlAnalyticsResponse`).

### GET `/api/v1/admin/analytics/urls/{id}/clicks`

Raw events for any URL. Query `limit` (default 20, max 100).

### GET `/api/v1/admin/analytics/urls/{id}/click-trend`

Daily counts for any URL. Query `days` (7/30/90).

### GET `/api/v1/admin/analytics/click-trend`

System-wide daily counts. Query `days` (7/30/90).

### GET `/api/v1/admin/analytics/recent-clicks`

System-wide recent events. Query `limit` (default 10, 1–100).

---

## Actuator

| Process | Path | Prod / docker |
|---------|------|----------------|
| App :8081 | `/actuator/health`, `/health/liveness`, `/health/readiness` | Public to the private network |
| App :8081 | `/actuator/prometheus`, `/metrics/**` | Only if `LINKFLOW_METRICS_PUBLIC=true` |
| App :8081 | Other actuator | Denied |
| Gateway :8080 | `/actuator/health` | Local to the gateway — not proxied to the app |
| Nginx | `/nginx-health` | Edge liveness |
| Nginx | `/actuator` | **Denied** |

Swagger/OpenAPI: disabled and denied outside `dev`.

---

## Web UI routes (`linkflow-web`)

Server-rendered HTML unless noted. Not the REST API. All backend calls go through `LINKFLOW_GATEWAY_URL`.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/`, `/login`, `/register`, `/check-email`, `/forgot-password`, `/reset-password` | Public |
| POST | `/login`, `/register`, `/logout` | Auth forms |
| GET | `/verify-email`, `/verify-email-change` | Token landing pages |
| POST | `/resend-verification`, `/forgot-password`, `/reset-password` | Recovery forms |
| GET | `/dashboard` | User home |
| GET/POST | `/urls`, `/urls/new`, `/urls/bulk`, `/urls/{id}`, `/urls/{id}/edit`, `/urls/{id}/delete`, `/urls/{id}/reactivate` | URL management |
| GET | `/urls/{id}/analytics`, `/urls/{id}/qr-proxy` | Analytics + QR proxy |
| GET/POST | `/profile`, `/profile/change-password`, `/profile/request-email-change` | Profile |
| GET | `/admin`, `/admin/users`, `/admin/users/{id}`, `/admin/urls`, `/admin/urls/{id}`, `/admin/analytics`, `/admin/system` | Admin |
| POST | `/admin/users/{id}/disable`, `enable`, `delete`, `roles`; `/admin/urls/{id}/deactivate`, `reactivate` | Admin mutations |
| GET | `/tools/rate-limit` | Rate-limit demo page |
| GET | `/tools/rate-limit/probe` | JSON probe (`@ResponseBody`) |

Error pages: `/error` mapped to 404/500 templates.

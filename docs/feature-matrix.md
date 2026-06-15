# LinkFlow Feature Matrix

Maps each feature to code entry points, persistence, and security. Verified against controllers and services.

| Feature | Business purpose | Entry point | Controllers | Services | Repositories | DB tables | Redis | Security |
|---------|------------------|-------------|-------------|----------|--------------|-----------|-------|----------|
| User registration | Create account | `POST /api/v1/auth/register` | `AuthController` | `AuthService`, `UserLookupPort` | via `UserLookupAdapter` → `UserRepository` | `users`, `user_roles` | — | Public; IP rate limit |
| User login | Obtain tokens | `POST /api/v1/auth/login` | `AuthController` | `AuthService`, `JwtService`, `RefreshTokenService` | `UserRepository`, `RefreshTokenRepository` | `users`, `refresh_tokens` | — | Public; IP rate limit |
| Token refresh | Extend session | `POST /api/v1/auth/refresh` | `AuthController` | `AuthService`, `RefreshTokenService` | `RefreshTokenRepository` | `refresh_tokens` | — | Public; rotation |
| Logout | Revoke refresh token | `POST /api/v1/auth/logout` | `AuthController` | `AuthService`, `RefreshTokenService` | `RefreshTokenRepository` | `refresh_tokens` | — | Bearer required |
| View profile | Read own user | `GET /api/v1/users/me` | `UserController` | `UserService` | `UserRepository` | `users` | — | Authenticated |
| Update profile | Change name | `PUT /api/v1/users/me` | `UserController` | `UserService` | `UserRepository` | `users` | — | Authenticated |
| Create short URL | Shorten link | `POST /api/v1/urls` | `UrlController` | `UrlService`, `IdempotencyService`, `ShortCodeGenerator`, `RedisLockService` | `ShortUrlRepository`, `IdempotencyRecordRepository` | `short_urls`, `idempotency_records` | Alias lock | Bearer; optional idempotency |
| Bulk create URLs | Batch shorten | `POST /api/v1/urls/bulk` | `UrlController` | `UrlService`, `IdempotencyService` | same | same | Alias lock | Bearer; required idempotency key |
| List my URLs | Paginated inventory | `GET /api/v1/urls` | `UrlController` | `UrlService` | `ShortUrlRepository` | `short_urls` | — | Owner scope in query |
| Get URL details | Single URL | `GET /api/v1/urls/{id}` | `UrlController` | `UrlService` | `ShortUrlRepository` | `short_urls` | — | Owner check |
| Update URL | Change expiry/active | `PATCH /api/v1/urls/{id}` | `UrlController` | `UrlService`, `UrlCacheService` | `ShortUrlRepository` | `short_urls` | Cache evict | Owner check |
| Delete URL | Soft delete | `DELETE /api/v1/urls/{id}` | `UrlController` | `UrlService`, `UrlCacheService` | `ShortUrlRepository` | `short_urls` | Cache evict | Owner check |
| QR code | PNG for short link | `GET /api/v1/urls/{id}/qr` | `UrlController` | `UrlService`, `QrCodeService` | `ShortUrlRepository` | `short_urls` | — | Owner check |
| Public redirect | Follow short link | `GET /r/{shortCode}` | `RedirectController` | `RedirectService`, `UrlCacheService`, `ClickTrackingPort` | `ShortUrlRepository` | `short_urls` | Cache read/write (with SWR, jitter, negative cache, stampede lock) | Public; IP rate limit |
| Click tracking | Record analytics | (internal) | — | `ClickTrackingService` | `ClickEventRepository`, `UrlAnalyticsRepository` | `click_events`, `url_analytics` | Stream (buffer), Hash (counter), Set (active) | Async on redirect |
| Per-URL analytics | Owner stats | `GET /api/v1/urls/{id}/analytics` | `AnalyticsController` | `AnalyticsQueryService` | `UrlAnalyticsRepository` | `url_analytics`, `short_urls` | — | Owner check |
| Recent click events | Owner click history | `GET /api/v1/urls/{id}/analytics/clicks` | `AnalyticsController` | `AnalyticsQueryService` | `ClickEventRepository` | `click_events` | — | Owner check |
| Click trend graph (user) | Daily clicks over time | `GET /api/v1/urls/{id}/analytics/click-trend` | `AnalyticsController` | `AnalyticsQueryService` | `ClickEventRepository` | `click_events` | — | Owner check |
| Recent activity feed (user) | User clicks history | `GET /api/v1/analytics/recent-clicks` | `AnalyticsController` | `AnalyticsQueryService` | `ClickEventRepository` | `click_events`, `short_urls` | — | Authenticated; IP masking |
| Top URLs (user) | Rank by clicks | `GET /api/v1/analytics/top` | `AnalyticsController` | `AnalyticsQueryService` | `UrlAnalyticsRepository` | `url_analytics` | — | Authenticated |
| Admin list users | Operator view | `GET /api/v1/admin/users` | `AdminUserController` | `UserService` | `UserRepository` | `users` | — | ROLE_ADMIN |
| Admin user detail | Single user | `GET /api/v1/admin/users/{id}` | `AdminUserController` | `UserService` | `UserRepository` | `users` | — | ROLE_ADMIN |
| Admin disable user | Block login | `PATCH /api/v1/admin/users/{id}/disable` | `AdminUserController` | `UserService` | `UserRepository` | `users` | — | ROLE_ADMIN |
| Admin enable user | Restore access | `PATCH /api/v1/admin/users/{id}/enable` | `AdminUserController` | `UserService` | `UserRepository` | `users` | — | ROLE_ADMIN |
| Admin soft-delete user | Remove account | `DELETE /api/v1/admin/users/{id}` | `AdminUserController` | `UserService` | `UserRepository` | `users` | — | ROLE_ADMIN |
| Admin list URLs | All links | `GET /api/v1/admin/urls` | `AdminUrlController` | `UrlService` | `ShortUrlRepository` | `short_urls` | — | ROLE_ADMIN |
| Admin deactivate URL | Force disable | `PATCH /api/v1/admin/urls/{id}/deactivate` | `AdminUrlController` | `UrlService` | `ShortUrlRepository` | `short_urls` | Cache evict | ROLE_ADMIN |
| System stats | Platform metrics | `GET /api/v1/admin/analytics/stats` | `AdminAnalyticsController` | `AnalyticsQueryService` | `StatsRepository` | multiple | — | ROLE_ADMIN |
| System top URLs | Platform ranking | `GET /api/v1/admin/analytics/top` | `AdminAnalyticsController` | `AnalyticsQueryService` | `UrlAnalyticsRepository` | `url_analytics` | — | ROLE_ADMIN |
| Admin click history | Any URL clicks | `GET /api/v1/admin/analytics/urls/{id}/clicks` | `AdminAnalyticsController` | `AnalyticsQueryService` | `ClickEventRepository` | `click_events` | — | ROLE_ADMIN |
| Admin click trend | Daily clicks per URL | `GET /api/v1/admin/analytics/urls/{id}/click-trend` | `AdminAnalyticsController` | `AnalyticsQueryService` | `ClickEventRepository` | `click_events` | — | ROLE_ADMIN |
| System click trend | Daily clicks platform-wide | `GET /api/v1/admin/analytics/click-trend` | `AdminAnalyticsController` | `AnalyticsQueryService` | `ClickEventRepository` | `click_events` | — | ROLE_ADMIN |
| System recent clicks | Platform-wide click feed | `GET /api/v1/admin/analytics/recent-clicks` | `AdminAnalyticsController` | `AnalyticsQueryService` | `ClickEventRepository` | `click_events`, `short_urls` | — | ROLE_ADMIN; unmasked raw IP |
| Rate limiting | Abuse protection | (filter) | — | `RateLimitService` | — | — | Lua sliding-window sorted sets | All non-excluded paths |
| Expiry cleanup | Deactivate expired | (scheduled) | — | `UrlService` | `ShortUrlRepository`, `IdempotencyRecordRepository` | `short_urls`, `idempotency_records` | — | Internal job |
| Admin bootstrap | First admin | (startup) | — | `AdminBootstrap` | `UserRepository` | `users`, `user_roles` | — | Env-gated |
| Health / metrics | Operability | `/actuator/health`, optional metrics | — | `RedisHealthIndicator` | — | — | PING | Profile-based; see system-design.md |
| Web login UI | SSR auth | `POST /login` | `AuthController` (web) | `AuthApiClient` | — | — | — | Session + CSRF |
| Web URL management | SSR CRUD | `/urls/**` | `UrlController` (web) | `UrlApiClient` | — | — | — | Session JWT |
| Rate limit demo | Engineering tool | `/tools/rate-limit` | `ToolsController` | `ActuatorApiClient` | — | — | — | Authenticated |
| QR proxy (web) | Hide token from img | `GET /urls/{id}/qr-proxy` | `UrlController` (web) | `UrlApiClient` | — | — | — | Session JWT |

---

## Deliberate non-goals (verified)

| Feature | Status |
|---------|--------|
| Admin list URLs for specific user | Not planned for v1 |
| Advanced browser/OS charts & geo maps | Not planned — daily aggregated click trends (7d/30d/90d) and recent click feeds are implemented |
| Runtime role assignment API | Roles fixed at registration/bootstrap |
| Email verification | Not implemented |
| OAuth2 / SSO | Not implemented |
| Kubernetes manifests | Not in repository — see deployment.md |
| Spring Session Redis | Not implemented — in-memory web sessions |

---

## Related documents

- [api-inventory.md](api-inventory.md)
- [database-design.md](database-design.md)
- [system-design.md](system-design.md)

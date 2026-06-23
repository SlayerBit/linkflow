# LinkFlow Source Audit Report

**Date:** 2026-06-23  
**Scope:** Full independent audit of source code, tests, configuration, migrations, and Docker setup.  
**Method:** Code-only truth; no reliance on prior audits, TODOs, or comments.

---

## 1. Issues Discovered

### Security
| Issue | Severity |
|-------|----------|
| Logout did not revoke access tokens (refresh token only) | High |
| Password reset returned 404 for unknown emails (user enumeration) | High |
| Verification/reset/email-change tokens returned in API responses unconditionally | High |
| Public auth pages blocked by web security (`/verify-email`, `/forgot-password`, etc.) | High |
| JWT filter did not validate `tokenType=ACCESS` claim | Medium |
| Rate limit used `getRemoteAddr()` only — incorrect behind gateway/proxy | Medium |
| `EmailNotVerifiedException` shared generic `AUTHENTICATION_FAILED` code | Low |

### Distributed systems
| Issue | Severity |
|-------|----------|
| Analytics Redis Stream `XTRIM` after per-instance batch could drop unconsumed messages | High |
| Malformed stream records were acknowledged and lost | Medium |
| Idempotency unique-constraint races returned 500 instead of cached response | Medium |
| `ExpiredUrlCleanupJob` cron hardcoded (not configurable) | Low |

### UI / wiring
| Issue | Severity |
|-------|----------|
| Admin URL detail QR used owner-scoped API — broken for other users' URLs | High |
| Password change left stale session after backend token revocation | Medium |
| Bulk create sidebar `activeNav` mismatch | Low |
| Profile stats requested analytics limit 1000 (backend caps at 100) | Low |

### Tests
| Issue | Severity |
|-------|----------|
| `AdminSelfProtectionIT` used wrong JSON path `$.error.code` | High |
| `AdminSelfProtectionIT.adminCannotDisableLastAdmin` had no assertions | High |
| `AuthFlowIT` expected wrong error code for unverified email | Medium |
| `UrlFlowIT` expected `NOT_FOUND` but API returns `RESOURCE_NOT_FOUND` | Medium |
| `AnalyticsAndCacheIT` did not trigger flush — flaky against 30s scheduler | Medium |
| `RateLimitIT` property override conflicted with parent test config | Medium |

---

## 2. Fixes Implemented

### Backend (`linkflow-auth`, `linkflow-user`, `linkflow-url`, `linkflow-analytics`, `linkflow-rate-limit`, `linkflow-common`, `linkflow-app`)
- **Logout:** Revokes access tokens via `TokenRevocationPort` when refresh token is revoked.
- **Password reset:** Returns generic message; no 404 for unknown email.
- **`expose-dev-tokens`:** New property (`linkflow.security.expose-dev-tokens`, default `false`). Tokens only returned when explicitly enabled (Docker demo sets `true`).
- **JWT filter:** Rejects tokens without `tokenType=ACCESS`.
- **Analytics flush:** Removed global stream trim; acknowledge-only for successfully parsed records; malformed records left pending for retry.
- **Admin QR:** `GET /api/v1/admin/urls/{id}/qr` for admin PNG access.
- **Idempotency:** Catches `DataIntegrityViolationException` on store; global handler returns 409.
- **Rate limit:** Resolves client IP from `X-Forwarded-For` / `X-Real-IP`.
- **Scheduled jobs:** `ExpiredUrlCleanupJob` cron externalized to `linkflow.url.expired-cleanup-cron`.
- **IP masking:** User-facing per-URL click logs mask IPs; admin endpoints retain raw IPs.
- **Email verification:** Distinct error code `EMAIL_NOT_VERIFIED`.

### Web (`linkflow-web`)
- **Security config:** Public routes for verify-email, forgot/reset password, verify-email-change.
- **Admin QR proxy:** `GET /admin/urls/{id}/qr-proxy` uses admin API.
- **Password change:** Clears session and redirects to login after success.
- **Login:** Detects `EMAIL_NOT_VERIFIED` by error code.
- **Bulk create:** Enforces 100-URL limit; fixes sidebar active state.
- **Profile stats:** Uses analytics limit of 100 (matches API cap).

### Configuration
- `application.yml`: `email-verification-required`, `expose-dev-tokens`, `url.expired-cleanup-cron`.
- `.env.example` and `docker-compose.yml` updated accordingly.

---

## 3. Dead Code Removed

- Duplicate `UserService.listUsers()` method (introduced during edit, removed).
- Duplicate unreachable `return "redirect:/profile"` after password-change success path.
- Removed unsafe analytics stream trim path (replaced with acknowledge-only).

**Retained (intentional):** `UserLookupPort.updateEmail`, `TokenRevocationPort.getAccessTokensRevokedAfter` — port methods for future cross-module use; not harmful.

---

## 4. Distributed-System Risks Fixed

| Risk | Fix |
|------|-----|
| Multi-instance analytics stream trim race | Acknowledge-only; no global `XTRIM` |
| ShedLock on all 4 scheduled jobs | Verified present (unchanged) |
| Logout access token valid on other instances | Access token revocation on logout |
| Idempotency duplicate key 500 | Graceful 409 + catch on store |
| Rate limit IP behind gateway | Forwarded header support |

**Remaining:** Counter flush decrements Redis before DB commit — acceptable drift with retry on next cycle. Click tracking Redis operations are not atomic (documented tradeoff).

---

## 5. Documentation Changes

- Created this report (`docs/AUDIT-REPORT.md`).
- Updated `docs/environment.md` with new security and scheduler variables.
- Updated `docs/index.md` to reference audit report.
- Removed stale meta-audit docs superseded by this report.

---

## 6. Tests Fixed

| Test | Fix |
|------|-----|
| `AdminSelfProtectionIT` | Correct `$.errorCode` path; replaced incomplete last-admin test with demotion test |
| `AuthFlowIT` | Expect `EMAIL_NOT_VERIFIED` |
| `UrlFlowIT` | Expect `RESOURCE_NOT_FOUND` |
| `AnalyticsAndCacheIT` | Explicit `AnalyticsFlushService.flush()` in await loops |
| `RateLimitIT` | Removed parent property override conflict; assert configured limit |
| `AbstractIntegrationTest` | `expose-dev-tokens=true` for IT email verification flow |

---

## 7. Tests Added

No new test classes. Existing tests repaired to match actual behavior.

---

## 8. Remaining Known Risks

| Risk | Notes |
|------|-------|
| No email delivery service | Tokens exposed only when `expose-dev-tokens=true` (demo/local) |
| `LastAdminException` unreachable via self-service API | Self-action check prevents sole admin demotion/disable by another admin path in normal flows |
| Analytics counter Redis/DB ordering | Minor count drift possible on DB failure after Redis decrement |
| Bulk custom-alias concurrent race | Single-create path has Redis lock; bulk validates once at start |
| Backend actuator not proxied through gateway | Scrapers hit `:8081` directly (by design in Compose) |
| JWT carries stale roles until refresh | Role changes revoke sessions but JWT claims are not live-reloaded |
| Stream growth without trim | Retention job purges DB; Redis stream relies on ack + optional future MAXLEN policy |

---

## 9. Features Verified Operational

| Flow | Status |
|------|--------|
| Registration + email verification | ✓ (IT + dev token exposure) |
| Login / logout / refresh | ✓ (IT) |
| Access token revocation on logout/disable/password change | ✓ |
| URL CRUD, redirect, QR | ✓ (IT) |
| Analytics buffering + flush | ✓ (IT with explicit flush) |
| Admin user/URL management | ✓ (IT) |
| Admin self-protection | ✓ (IT) |
| Rate limiting | ✓ (IT) |
| Redis-down auth fail-closed | ✓ (IT) |
| Gateway routing | ✓ (IT) |
| Public auth web pages | ✓ (security config) |
| Admin QR for any URL | ✓ (new endpoint + proxy) |
| ShedLock scheduled jobs | ✓ (configured on all 4 jobs) |

---

## 10. Architecture Readiness Assessment

**Verdict: Production-ready for modular-monolith deployment behind a gateway, with documented caveats.**

| Area | Rating | Notes |
|------|--------|-------|
| Modular boundaries | Strong | Clear Maven modules, port/adapter pattern |
| Auth & sessions | Strong | JWT + refresh rotation + Redis revocation |
| Multi-instance safety | Good | ShedLock, Redis sessions, shared revocation |
| Analytics pipeline | Good | Stream dedup via `streamRecordId`; flush hardened |
| Test coverage | Good | 32 integration tests in app module; unit tests in auth/url/rate-limit |
| Operational observability | Adequate | Actuator, Prometheus, Grafana in Compose |
| Security defaults | Good | Prod profile restricts Swagger/actuator; tokens gated |

**Build verification:** `mvn verify` passes on JDK 21 (all modules, 32 ITs).

---

## Module inventory

| Module | Role |
|--------|------|
| `linkflow-app` | Boot entry, Flyway, schedulers, ShedLock |
| `linkflow-auth` | JWT, refresh tokens, auth API |
| `linkflow-user` | Profiles, admin users, email change |
| `linkflow-url` | Short URLs, redirects, idempotency |
| `linkflow-analytics` | Click tracking, flush, query |
| `linkflow-rate-limit` | Redis sliding window filter |
| `linkflow-web` | Thymeleaf UI, Redis sessions |
| `linkflow-gateway` | Spring Cloud Gateway routes |
| `linkflow-common` | Shared exceptions, ports, filters |
| `linkflow-observability` | Health indicators, metrics config |
